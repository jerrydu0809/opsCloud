package com.baiyi.opscloud.datasource.consul.provider;

import com.baiyi.opscloud.common.annotation.SingleTask;
import com.baiyi.opscloud.common.constants.SingleTaskConstants;
import com.baiyi.opscloud.common.constants.enums.DsTypeEnum;
import com.baiyi.opscloud.common.datasource.ConsulConfig;
import com.baiyi.opscloud.core.factory.AssetProviderFactory;
import com.baiyi.opscloud.core.model.DsInstanceContext;
import com.baiyi.opscloud.core.provider.asset.BaseAssetProvider;
import com.baiyi.opscloud.core.util.AssetUtil;
import com.baiyi.opscloud.datasource.consul.driver.ConsulServiceDriver;
import com.baiyi.opscloud.datasource.consul.entity.ConsulService;
import com.baiyi.opscloud.domain.constants.DsAssetTypeConstants;
import com.baiyi.opscloud.domain.generator.opscloud.DatasourceConfig;
import com.baiyi.opscloud.domain.generator.opscloud.DatasourceInstanceAsset;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author baiyi
 * @Date 2022/7/14 15:09
 * @Version 1.0
 */
@Slf4j
@Component
public class ConsulServiceProvider extends BaseAssetProvider<ConsulService.Service> {

    @Resource
    private ConsulServiceProvider consulServiceProvider;

    @Resource
    private ConsulServiceDriver consulServiceDriver;

    @Override
    public String getInstanceType() {
        return DsTypeEnum.CONSUL.name();
    }

    @Override
    public String getAssetType() {
        return DsAssetTypeConstants.CONSUL_SERVICE.name();
    }

    private ConsulConfig.Consul buildConfig(DatasourceConfig dsConfig) {
        return dsConfigHelper.build(dsConfig, ConsulConfig.class).getConsul();
    }

    @Override
    protected List<ConsulService.Service> listEntities(DsInstanceContext dsInstanceContext) {
        List<ConsulService.Service> services = Lists.newArrayList();
        ConsulConfig.Consul consul = buildConfig(dsInstanceContext.getDsConfig());
        try {
            consul.getDcs().forEach(dc ->
                    services.addAll(
                            consulServiceDriver.listServices(buildConfig(dsInstanceContext.getDsConfig()), dc)
                                    .stream()
                                    .peek(e -> e.setDc(dc))
                                    .collect(Collectors.toList())
                    )
            );
            return services;
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        throw new RuntimeException("查询条目失败");
    }

    @Override
    @SingleTask(name = SingleTaskConstants.PULL_CONSUL_SERVICE, lockTime = "1m")
    public void pullAsset(int dsInstanceId) {
        doPull(dsInstanceId);
    }

    @Override
    protected boolean equals(DatasourceInstanceAsset asset, DatasourceInstanceAsset preAsset) {
        if (!AssetUtil.equals(preAsset.getName(), asset.getName()))
            return false;
        if (preAsset.getIsActive() != asset.getIsActive())
            return false;
        return true;
    }

    @Override
    public void afterPropertiesSet() {
        AssetProviderFactory.register(consulServiceProvider);
    }

}