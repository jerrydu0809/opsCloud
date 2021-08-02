package com.baiyi.opscloud.facade.server.impl;

import com.baiyi.opscloud.common.util.BeanCopierUtil;
import com.baiyi.opscloud.common.util.IdUtil;
import com.baiyi.opscloud.datasource.manager.ServerProviderManager;
import com.baiyi.opscloud.domain.DataTable;
import com.baiyi.opscloud.domain.annotation.TagClear;
import com.baiyi.opscloud.domain.generator.opscloud.Server;
import com.baiyi.opscloud.domain.param.server.ServerParam;
import com.baiyi.opscloud.domain.types.BusinessTypeEnum;
import com.baiyi.opscloud.domain.vo.server.ServerVO;
import com.baiyi.opscloud.event.handler.ServerEventHandler;
import com.baiyi.opscloud.event.param.ServerEventParam;
import com.baiyi.opscloud.facade.datasource.BusinessAssetRelationFacade;
import com.baiyi.opscloud.facade.server.ServerFacade;
import com.baiyi.opscloud.packer.server.ServerPacker;
import com.baiyi.opscloud.service.datasource.DsInstanceAssetService;
import com.baiyi.opscloud.service.server.ServerService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Author baiyi
 * @Date 2021/5/24 5:45 下午
 * @Version 1.0
 */
@Service
public class ServerFacadeImpl implements ServerFacade {

    @Resource
    private ServerService serverService;

    @Resource
    private ServerPacker serverPacker;

    @Resource
    private ServerEventHandler serverEventHandler;

    @Resource
    private ServerProviderManager serverProviderManager;

    @Resource
    private BusinessAssetRelationFacade businessAssetRelationFacade;

    @Override
    public DataTable<ServerVO.Server> queryServerPage(ServerParam.ServerPageQuery pageQuery) {
        DataTable<Server> table = serverService.queryServerPage(pageQuery);
        return new DataTable<>(serverPacker.wrapVOList(table.getData(), pageQuery), table.getTotalNum());
    }

    @Override
    public void addServer(ServerVO.Server server) {
        Server pre = toDO(server);
        ServerEventParam.update update = ServerEventParam.update.builder()
                .server(pre).build();
        serverEventHandler.updateHandle(update);
        serverService.add(pre);
        if (server.getAssetId() != null) {
            businessAssetRelationFacade.bindAsset(server);
        }
        serverProviderManager.create(pre);
    }



    @Override
    public void updateServer(ServerVO.Server server) {
        Server pre = toDO(server);
        ServerEventParam.update update = ServerEventParam.update.builder()
                .server(pre).build();
        serverEventHandler.updateHandle(update);
        serverService.update(pre);
        serverProviderManager.update(pre);

    }

    private Server toDO(ServerVO.Server server) {
        Server pre = BeanCopierUtil.copyProperties(server, Server.class);
        if (IdUtil.isEmpty(pre.getSerialNumber())) {
            Server maxSerialNumberServer = serverService.getMaxSerialNumberServer(pre.getServerGroupId(), pre.getEnvType());
            pre.setSerialNumber(null == maxSerialNumberServer ? 1 : maxSerialNumberServer.getSerialNumber() + 1);
        }
        return pre;
    }

    @TagClear(type = BusinessTypeEnum.SERVER)
    @Override
    public void deleteServerById(Integer id) {
        ServerEventParam.delete delete = ServerEventParam.delete.builder()
                .id(id).build();
        serverProviderManager.destroy(id);
        serverEventHandler.deleteHandle(delete);
        serverService.delete(id);
    }

    @Override
    public DataTable<ServerVO.Server> queryUserRemoteServerPage(ServerParam.UserRemoteServerPageQuery pageQuery) {
        DataTable<Server> table = serverService.queryUserRemoteServerPage(pageQuery);
        return new DataTable<>(serverPacker.wrapVOList(table.getData(), pageQuery), table.getTotalNum());
    }

}
