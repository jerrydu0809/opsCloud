package com.baiyi.caesar.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baiyi.caesar.terminal.builder.TerminalSessionBuilder;
import com.baiyi.caesar.common.model.HostInfo;
import com.baiyi.caesar.common.util.SessionUtil;
import com.baiyi.caesar.controller.base.BaseWebSocketController;
import com.baiyi.caesar.domain.generator.caesar.TerminalSession;
import com.baiyi.caesar.terminal.factory.TerminalProcessFactory;
import com.baiyi.caesar.service.terminal.TerminalSessionService;
import com.baiyi.caesar.terminal.enums.MessageState;
import com.baiyi.caesar.terminal.message.LoginMessage;
import com.baiyi.caesar.terminal.task.SentOutputTask;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author baiyi
 * @Date 2021/5/28 9:15 上午
 * @Version 1.0
 */
@Slf4j
@ServerEndpoint(value = "/ws/terminal")
@Component
public class WebTerminalController extends BaseWebSocketController {

    private static final AtomicInteger onlineCount = new AtomicInteger(0);
    // concurrent包的线程安全Set，用来存放每个客户端对应的Session对象。
    private static CopyOnWriteArraySet<Session> sessionSet = new CopyOnWriteArraySet<>();
    // 当前会话 uuid
    private String sessionId = null;

    private Session session = null;

    // 超时时间1H
    public static final Long WEBSOCKET_TIMEOUT = 60 * 60 * 1000L;

    private static HostInfo serverInfo = HostInfo.build();

    private static TerminalSessionService terminalSessionService;

    private TerminalSession terminalSession;

    @Autowired
    public void setTerminalSessionService(TerminalSessionService terminalSessionService) {
        WebTerminalController.terminalSessionService = terminalSessionService;
    }

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) {
        // this.sessionId = UUID.randomUUID().toString();
        log.info("终端会话尝试链接，sessionId = {}", sessionId);
        this.sessionId = UUID.randomUUID().toString();
        TerminalSession terminalSession = TerminalSessionBuilder.build(sessionId, serverInfo);
        terminalSessionService.add(terminalSession);
        this.terminalSession = terminalSession;
        sessionSet.add(session);
        int cnt = onlineCount.incrementAndGet(); // 在线数加1
        log.info("有连接加入，当前连接数为：{}", cnt);
        session.setMaxIdleTimeout(WEBSOCKET_TIMEOUT);
        this.session = session;
        // 线程启动
        Runnable run = new SentOutputTask(sessionId, session);
        Thread thread = new Thread(run);
        thread.start();
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        TerminalProcessFactory.getProcessByKey(MessageState.CLOSE.getState()).process("", session, terminalSession);
        sessionSet.remove(session);
        int cnt = onlineCount.decrementAndGet();
        log.info("有连接关闭，当前连接数为：{}", cnt);
    }

    /**
     * 收到客户端消息后调用的方法
     * Session session
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        if (!session.isOpen() || StringUtils.isEmpty(message)) return;
        // log.info("来自客户端的消息：{}", message);
        SessionUtil.setUsername(terminalSession.getUsername());
        JSONObject jsonObject = JSON.parseObject(message);
        String status = jsonObject.getString("status");
        // 鉴权并更新会话信息
        if (MessageState.LOGIN.getState().equals(status)) {
            LoginMessage terminalMessage = new GsonBuilder().create().fromJson(message, LoginMessage.class);
            setUser(authentication(terminalMessage.getToken()));
        }
        TerminalProcessFactory.getProcessByKey(status).process(message, session, terminalSession);
    }

    private void setUser(String username){
        if (StringUtils.isEmpty(terminalSession.getUsername())) {
            terminalSession.setUsername(username);
            terminalSessionService.update(terminalSession);
        }
    }

    /**
     * 出现错误
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("发生错误：{}，Session ID： {}", error.getMessage(), session.getId());
        error.printStackTrace();
    }

    /**
     * 发送消息，实践表明，每次浏览器刷新，session会发生变化。
     *
     * @param session
     * @param message
     */
    public static void sendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("发送消息出错：{}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 群发消息
     *
     * @param message
     * @throws IOException
     */
    public static void broadCastInfo(String message) throws IOException {
        for (Session session : sessionSet) {
            if (session.isOpen()) {
                sendMessage(session, message);
            }
        }
    }

    /**
     * 指定Session发送消息
     *
     * @param sessionId
     * @param message
     * @throws IOException
     */
    public static void sendMessage(String message, String sessionId) throws IOException {
        Session session = null;
        for (Session s : sessionSet) {
            if (s.getId().equals(sessionId)) {
                session = s;
                break;
            }
        }
        if (session != null) {
            sendMessage(session, message);
        } else {
            log.warn("没有找到你指定ID的会话：{}", sessionId);
        }
    }

}
