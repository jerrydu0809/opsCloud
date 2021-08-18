package com.baiyi.opscloud.event.customer.impl;

import com.baiyi.opscloud.ansible.ServerGroupingAlgorithm;
import com.baiyi.opscloud.domain.generator.opscloud.ServerGroup;
import com.baiyi.opscloud.domain.types.BusinessTypeEnum;
import com.baiyi.opscloud.event.NoticeEvent;
import com.baiyi.opscloud.util.ServerTreeUtil;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author baiyi
 * @Date 2021/8/18 10:17 上午
 * @Version 1.0
 */
@Component
public class ServerGroupEventCustomer extends AbstractEventConsumer<ServerGroup> {

    @Resource
    private ServerTreeUtil serverTreeUtil;

    @Resource
    private ServerGroupingAlgorithm serverGroupingAlgorithm;

    @Override
    public String getEventType() {
        return BusinessTypeEnum.SERVERGROUP.name();
    }

    @Override
    protected void preEventProcessing(NoticeEvent noticeEvent) {
        ServerGroup eventData = toEventData(noticeEvent.getMessage());
        serverGroupingAlgorithm.evictGrouping(eventData.getId());
        serverTreeUtil.evictWrap(eventData.getId());
    }

}