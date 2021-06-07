package com.baiyi.caesar.terminal.factory.impl;

import com.baiyi.caesar.domain.generator.caesar.TerminalSession;
import com.baiyi.caesar.sshcore.message.BaseMessage;
import com.baiyi.caesar.sshcore.message.LogoutMessage;
import com.baiyi.caesar.sshcore.model.JSchSessionContainer;
import com.baiyi.caesar.terminal.enums.MessageState;
import com.baiyi.caesar.terminal.factory.BaseProcess;
import com.baiyi.caesar.terminal.factory.ITerminalProcess;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

import javax.websocket.Session;

/**
 * @Author baiyi
 * @Date 2020/5/11 5:19 下午
 * @Version 1.0
 */
@Component
public class LogoutProcess extends BaseProcess implements ITerminalProcess {

    /**
     * 单个关闭
     *
     * @return
     */


    @Override
    public String getState() {
        return MessageState.LOGOUT.getState();
    }

    @Override
    public void process(String message, Session session, TerminalSession terminalSession) {
        LogoutMessage baseMessage = (LogoutMessage) getMessage(message);
        recordAuditLog(terminalSession, baseMessage.getInstanceId()); // 写审计日志
        closeSessionInstance(terminalSession, baseMessage.getInstanceId()); // 设置关闭会话

        JSchSessionContainer.closeSession(terminalSession.getSessionId(), baseMessage.getInstanceId());
    }

    @Override
    protected BaseMessage getMessage(String message) {
        return new GsonBuilder().create().fromJson(message, LogoutMessage.class);
    }
}
