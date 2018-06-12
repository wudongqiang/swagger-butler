package com.didispace.swagger.butler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import springfox.documentation.swagger.web.SwaggerResource;
import springfox.documentation.swagger.web.SwaggerResourcesProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SwaggerResourcesProcessor implements SwaggerResourcesProvider {

    @Autowired
    private RouteLocator routeLocator;
    @Autowired
    private DiscoveryClient discoveryClient;
    @Autowired
    private SwaggerButlerProperties swaggerButlerConfig;

    @Override
    public List<SwaggerResource> get() {
        List<SwaggerResource> resources = new ArrayList<>();

        List<Route> routes = routeLocator.getRoutes();
        for (Route route : routes) {
            String routeName = route.getId();

            SwaggerResourceProperties resourceProperties = swaggerButlerConfig.getResources().get(routeName);

            // 不用根据zuul的路由自动生成，并且当前route信息没有配置resource则不生成文档
            if (swaggerButlerConfig.getAutoGenerateFromZuulRoutes() == false && resourceProperties == null) {
                continue;
            }

            // 需要根据zuul的路由自动生成，但是当前路由名在忽略清单中（ignoreRoutes）或者不在生成清单中（generateRoutes）则不生成文档
            if (swaggerButlerConfig.getAutoGenerateFromZuulRoutes() == true && swaggerButlerConfig.needIgnore(routeName)) {
                continue;
            }
            // 处理swagger文档的名称
            String name = routeName;
            if (resourceProperties != null && resourceProperties.getName() != null) {
                name = resourceProperties.getName();
            }

            // 处理获取swagger文档的路径
            String swaggerPath = swaggerButlerConfig.getApiDocsPath();
            if (resourceProperties != null && resourceProperties.getApiDocsPath() != null) {
                swaggerPath = resourceProperties.getApiDocsPath();
            }
            String location = route.getFullPath().replace("/**", swaggerPath);

            // 处理swagger的版本设置
            String swaggerVersion = swaggerButlerConfig.getSwaggerVersion();
            if (resourceProperties != null && resourceProperties.getSwaggerVersion() != null) {
                swaggerVersion = resourceProperties.getSwaggerVersion();
            }

            if (swaggerButlerConfig.getAutoGenerateGroup()) {
                String url =discoveryClient.getLocalServiceInstance().getUri()+ "/"+ routeName + swaggerButlerConfig.getApiDocResource();
                resources.addAll(getSwaggerResources(url, name, location));
            } else if (resourceProperties != null && !CollectionUtils.isEmpty(resourceProperties.getGroups())) {
                for (String group : resourceProperties.getGroups()) {
                    resources.add(swaggerResource(name + "-" + group, location + "?group=" + group, swaggerVersion));
                }
            } else {
                resources.add(swaggerResource(name, location, swaggerVersion));
            }

        }

        return resources;
    }

    private SwaggerResource swaggerResource(String name, String location, String version) {
        SwaggerResource swaggerResource = new SwaggerResource();
        swaggerResource.setName(name);
        swaggerResource.setLocation(location);
        swaggerResource.setSwaggerVersion(version);
        return swaggerResource;
    }

    /**
     * 使用获取资源的方式获取对应的group
     * @param url
     * @param name
     * @param location
     * @return
     */
    private List<SwaggerResource> getSwaggerResources(String url, String name, String location) {
        SwaggerResource[] data = new RestTemplate().getForObject(url, SwaggerResource[].class);
        for (SwaggerResource resource : data) {
            //此处的resource.getName() 就是groupName
            resource.setLocation(location + "?group=" + resource.getName());
            resource.setName(name + "#" + resource.getName());
        }
        return Arrays.asList(data);
    }
}