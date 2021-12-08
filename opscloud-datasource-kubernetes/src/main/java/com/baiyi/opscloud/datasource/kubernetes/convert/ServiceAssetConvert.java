package com.baiyi.opscloud.datasource.kubernetes.convert;

import com.baiyi.opscloud.domain.builder.asset.AssetContainer;
import com.baiyi.opscloud.domain.builder.asset.AssetContainerBuilder;
import com.baiyi.opscloud.domain.generator.opscloud.DatasourceInstance;
import com.baiyi.opscloud.domain.generator.opscloud.DatasourceInstanceAsset;
import com.baiyi.opscloud.domain.types.DsAssetTypeEnum;
import com.google.common.base.Joiner;
import io.fabric8.kubernetes.api.model.Service;

/**
 * @Author baiyi
 * @Date 2021/12/7 6:11 PM
 * @Version 1.0
 */
public class ServiceAssetConvert {

    public static AssetContainer toAssetContainer(DatasourceInstance dsInstance, Service entity) {
        String namespace = entity.getMetadata().getNamespace();
        String name = entity.getMetadata().getName();
        /**
         * 为了兼容多集群中Service名称相同导致无法拉取资产
         * 资产id使用联合键 namespace:deploymentName
         */
        String assetId = Joiner.on(":").join(namespace, name);

        DatasourceInstanceAsset asset = DatasourceInstanceAsset.builder()
                .instanceUuid(dsInstance.getUuid())
                .assetId(assetId)
                .name(name)
                .assetKey(name)
                .assetKey2(namespace) // namespace
                .kind(entity.getKind())
                .assetType(DsAssetTypeEnum.KUBERNETES_SERVICE.name())
                .createdTime(DeploymentAssetConvert.toGmtDate(entity.getMetadata().getCreationTimestamp()))
                .build();

        return AssetContainerBuilder.newBuilder()
                .paramAsset(asset)
                .paramProperty("uid", entity.getMetadata().getUid())
                .paramProperty("clusterIP",entity.getSpec().getClusterIP())
                .build();
    }

}
