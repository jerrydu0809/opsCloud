package com.baiyi.opscloud.datasource.kubernetes.pre;

import com.baiyi.opscloud.common.datasource.KubernetesConfig;
import com.baiyi.opscloud.common.exception.common.OCException;
import com.baiyi.opscloud.datasource.kubernetes.base.BaseKubernetesTest;
import com.baiyi.opscloud.datasource.kubernetes.driver.KubernetesServiceDriver;
import com.baiyi.opscloud.domain.constants.ApplicationResTypeEnum;
import com.baiyi.opscloud.domain.constants.BusinessTypeEnum;
import com.baiyi.opscloud.domain.constants.DsAssetTypeConstants;
import com.baiyi.opscloud.domain.generator.opscloud.Application;
import com.baiyi.opscloud.domain.generator.opscloud.DatasourceInstanceAsset;
import com.baiyi.opscloud.domain.vo.application.ApplicationResourceVO;
import com.baiyi.opscloud.facade.application.ApplicationFacade;
import com.baiyi.opscloud.service.application.ApplicationService;
import com.baiyi.opscloud.service.datasource.DsInstanceAssetService;
import com.baiyi.opscloud.datasource.kubernetes.driver.KubernetesDeploymentDriver;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * @Author baiyi
 * @Date 2023/3/30 16:12
 * @Version 1.0
 */
@Slf4j
public class KubernetesPreTest extends BaseKubernetesTest {

    private final static String NAMESPACE = "pre";

    @Resource
    private DsInstanceAssetService dsInstanceAssetService;
    @Resource
    private ApplicationService applicationService;
    @Resource
    private ApplicationFacade applicationFacade;

    @Test
    void update() {
        KubernetesConfig kubernetesConfig = getConfigById(KubernetesClusterConfigs.EKS_PROD);
        List<Deployment> deploymentList = KubernetesDeploymentDriver.list(kubernetesConfig.getKubernetes(), NAMESPACE);
        for (int i = 0; i < deploymentList.size(); i++) {
            // index namespace name
            String appName = deploymentList.get(i).getMetadata().getName();

            print(String.format("%s %s %s", i, deploymentList.get(i).getMetadata().getNamespace(), appName));

            Deployment deployment = deploymentList.get(i);
            Optional<Container> optionalContainer =
                    deployment.getSpec().getTemplate().getSpec().getContainers().stream().filter(c -> c.getName().equals("consul-agent")).findFirst();
            if (optionalContainer.isPresent()) {
                Container container = optionalContainer.get();
                container.getArgs().clear();
                List<String> args = Lists.newArrayList();
                args.add("agent");
                args.add("-bind=$(POD_IP)");
                // args.add("-node=posp-admin-$(POD_IP)");

                args.add("-node=" + appName + "-$(POD_IP)");

                args.add("-retry-join=172.30.151.77");
                args.add("-retry-join=172.30.153.69");
                args.add("-retry-join=172.30.155.237");
                args.add("-client=0.0.0.0");
                args.add("-ui");
                container.setArgs(args);
                KubernetesDeploymentDriver.update(kubernetesConfig.getKubernetes(), NAMESPACE, deployment);
            } else {
                print("consul-agent不存在");
            }

        }
    }


    /**
     * 单个Deployment(Canary) 启用ARMS
     */

    private void oneTest(String appName) {
        KubernetesConfig kubernetesConfig = getConfigById(KubernetesClusterConfigs.EKS_PROD);

        final String deploymentName = appName + "-1";
        /**
         * ARMS中应用的名称
         */
        final String armsAppName = appName + "-prod";

        Deployment deployment = KubernetesDeploymentDriver.get(kubernetesConfig.getKubernetes(), NAMESPACE, deploymentName);
        if (deployment == null) return;
        /**
         * 移除X-Ray容器
         */
        for (int i = 0; i < deployment.getSpec().getTemplate().getSpec().getContainers().size(); i++) {
            if (deployment.getSpec().getTemplate().getSpec().getContainers().get(i).getName().equals("adot-collector")) {
                deployment.getSpec().getTemplate().getSpec().getContainers().remove(i);
                break;
            }
        }

        /**
         * 查询应用容器
         */
        Optional<Container> optionalContainer = deployment.getSpec().getTemplate().getSpec().getContainers().stream().filter(c -> c.getName().startsWith(appName)).findFirst();
        if (!optionalContainer.isPresent()) {
            print("未找到容器: 退出");
            return;
        }

        List<EnvVar> srcEnvVars = optionalContainer.get().getEnv();
        List<EnvVar> newEnvVars = Lists.newArrayList();

        /**
         * 设置环境变量 $APP_NAME
         */
        EnvVar appNameEnvVar = new EnvVar("APP_NAME", armsAppName, null);
        newEnvVars.add(appNameEnvVar);

        for (EnvVar srcEnvVar : srcEnvVars) {
            /**
             * 下线X-Ray
             */
            if (srcEnvVar.getName().equals("JAVA_TOOL_OPTIONS")) {
                continue;
            }
            if (srcEnvVar.getName().equals("OTEL_TRACES_SAMPLER")) {
                continue;
            }
            if (srcEnvVar.getName().equals("OTEL_TRACES_SAMPLER_ARG")) {
                continue;
            }
            if (srcEnvVar.getName().equals("OTEL_OTLP_ENDPOINT")) {
                continue;
            }
            if (srcEnvVar.getName().equals("OTEL_RESOURCE")) {
                continue;
            }
            if (srcEnvVar.getName().equals("OTEL_RESOURCE_ATTRIBUTES")) {
                continue;
            }
            if (srcEnvVar.getName().equals("APP_NAME")) {
                continue;
            }
            /**
             * 启用ARMS
             */
            if (srcEnvVar.getName().equals("JAVA_JVM_AGENT")) {
                srcEnvVar.setValue("-javaagent:/jmx_prometheus_javaagent-0.16.1.jar=9999:/prometheus-jmx-config.yaml -javaagent:/arms-agent/arms-bootstrap-1.7.0-SNAPSHOT.jar -Darms.licenseKey=ib04e3ad3a@2a60bfc4abfe2d0 -Darms.appName=$(APP_NAME)");
            }
            newEnvVars.add(srcEnvVar);
        }
        optionalContainer.get().getEnv().clear();
        /**
         * 重新设置环境变量
         */
        optionalContainer.get().setEnv(newEnvVars);
        /**
         * 更新 Deployment
         */
        KubernetesDeploymentDriver.create(kubernetesConfig.getKubernetes(), NAMESPACE, deployment);
        print("---------------------------------------------------------------------------");
        print("应用名称: " + appName);
        print("---------------------------------------------------------------------------");

    }

    @Test
    void createPreDept() {
        KubernetesConfig prodConfig = getConfigById(KubernetesClusterConfigs.EKS_PROD);

        List<Deployment> deploymentList = KubernetesDeploymentDriver.list(prodConfig.getKubernetes(), NAMESPACE);
        KubernetesConfig preConfig = getConfigById(KubernetesClusterConfigs.EKS_PRE);
        deploymentList.forEach(deployment -> {
            KubernetesDeploymentDriver.create(preConfig.getKubernetes(), NAMESPACE, deployment);
        });
    }

    @Test
    void createPreService() {
        KubernetesConfig prodConfig = getConfigById(KubernetesClusterConfigs.EKS_PROD);
        List<Service> serviceList = KubernetesServiceDriver.list(prodConfig.getKubernetes(), NAMESPACE);
        KubernetesConfig preConfig = getConfigById(KubernetesClusterConfigs.EKS_PRE);
        serviceList.forEach(service -> {
            service.getMetadata().setUid(null);
            service.getMetadata().setResourceVersion(null);
            service.getSpec().setClusterIP(null);
            service.getSpec().setClusterIPs(null);
            KubernetesServiceDriver.create(preConfig.getKubernetes(), service);
        });
    }


    @Test
    void bindPreRes() {
        List<DatasourceInstanceAsset> assetList = dsInstanceAssetService.listByInstanceAssetType("3592e35e387f45adac441878cebdf219", DsAssetTypeConstants.KUBERNETES_DEPLOYMENT.name());
        assetList.forEach(a -> {
            Application application = applicationService.getByName(a.getName());
            ApplicationResourceVO.Resource resource = ApplicationResourceVO.Resource.builder()
                    .applicationId(application.getId())
                    .businessId(a.getId())
                    .businessType(BusinessTypeEnum.ASSET.getType())
                    .checked(false)
                    .comment(a.getAssetId())
                    .name(a.getAssetId())
                    .resourceType(ApplicationResTypeEnum.KUBERNETES_DEPLOYMENT.name())
                    .virtualResource(false)
                    .build();
            try {
                applicationFacade.bindApplicationResource(resource);
            } catch (OCException exception) {
                log.info("应用已绑定，{}", a.getName());
            }
        });
    }
}