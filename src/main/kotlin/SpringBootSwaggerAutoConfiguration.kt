package io.toolisticon.springboot.swagger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.plugin.core.OrderAwarePluginRegistry
import org.springframework.plugin.core.PluginRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.DocumentationPlugin
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2

@ConditionalOnProperty(prefix = "swagger", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@Configuration
@EnableSwagger2
@Import(BeanValidatorPluginsConfiguration::class)
@EnableConfigurationProperties(SwaggerProperties::class)
class SpringBootSwaggerAutoConfiguration(val properties: SwaggerProperties) {

  companion object {
    val logger: Logger = LoggerFactory.getLogger(SwaggerProperties::class.java)
    const val DUMMY = "DUMMY"
  }

  // Dummy Object to foster injection of List<DocumentationPlugin>, removed during initialization
  @Bean
  fun dummyDocket(): Docket = Docket(DocumentationType.SWAGGER_2).groupName(DUMMY).select().build()

  @Bean
  @Primary
  @Qualifier("documentationPluginRegistry")
  fun swaggerDocumentationPluginRegistry(beanDockets: MutableList<DocumentationPlugin>): PluginRegistry<DocumentationPlugin, DocumentationType> {
    val plugins = beanDockets.filter { it.groupName != SpringBootSwaggerAutoConfiguration.DUMMY }.toMutableList()

    properties.dockets.map {
      Docket(DocumentationType.SWAGGER_2)
        .groupName(it.key)
        .apiInfo(it.value.apiInfo.get())
        .select()
        .apis(RequestHandlerSelectors.basePackage(it.value.basePackage))
        .paths(PathSelectors.ant(it.value.path))
        .build()
    }.filter {
      plugins.filter { p -> p.groupName == it.groupName }.isEmpty()
    }.map {
      plugins.add(it)
    }

    logger.info("Register swagger-dockets: {}", plugins.map { it.groupName })

    return OrderAwarePluginRegistry.create(plugins)
  }

  /**
   * Redirects from root to swagger-ui.html, but only if `swagger.enabled=true` is set.
   */
  @ConditionalOnProperty(
    prefix = "swagger",
    name = ["redirect"],
    matchIfMissing = false
  )
  @Bean
  fun redirectSwaggerUI() = object : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
      registry.addRedirectViewController("/", "/swagger-ui.html")
      super.addViewControllers(registry)
    }
  }
}
