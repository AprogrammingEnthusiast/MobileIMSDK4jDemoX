/*
 * Copyright (C) 2020  即时通讯网(52im.net) & Jack Jiang.
 * The MobileIMSDK_X_netty (MobileIMSDK v4.x MINA版) Project. 
 * All rights reserved.
 * 
 * > Github地址：https://github.com/JackJiang2011/MobileIMSDK
 * > 文档地址：  http://www.52im.net/forum-89-1.html
 * > 技术社区：  http://www.52im.net/
 * > 技术交流群：320837163 (http://www.52im.net/topic-qqgroup.html)
 * > 作者公众号：“即时通讯技术圈】”，欢迎关注！
 * > 联系作者：  http://www.52im.net/thread-2792-1-1.html
 *  
 * "即时通讯网(52im.net) - 即时通讯开发者社区!" 推荐开源工程。
 * 
 * ClientCoreSDK.java at 2020-4-15 22:53:59, code by Jack Jiang.
 */
package net.openmob.mobileimsdk.java;

import net.openmob.mobileimsdk.java.core.AutoReLoginDaemon;
import net.openmob.mobileimsdk.java.core.KeepAliveDaemon;
import net.openmob.mobileimsdk.java.core.LocalUDPDataReciever;
import net.openmob.mobileimsdk.java.core.LocalUDPSocketProvider;
import net.openmob.mobileimsdk.java.core.QoS4ReciveDaemon;
import net.openmob.mobileimsdk.java.core.QoS4SendDaemon;
import net.openmob.mobileimsdk.java.event.ChatBaseEvent;
import net.openmob.mobileimsdk.java.event.ChatTransDataEvent;
import net.openmob.mobileimsdk.java.event.MessageQoSEvent;

public class ClientCoreSDK
{
	private final static String TAG = ClientCoreSDK.class.getSimpleName();

	/** rue表示开启MobileIMSDK Debug信息在Logcat下的输出，否则关闭。 **/
	public static boolean DEBUG = true;
	/**
	 * 是否在登陆成功后掉线时自动重新登陆线程中实质性发起登陆请求，true表示将在线程 运行周期中正常发起，否则不发起（即关闭实质性的重新登陆请求）
	 *
	 * 什么样的场景下，需要设置本参数为false？比如：上层应用可以在自已的节电逻辑中控 制当网络长时断开时就不需要实质性发起登陆请求了，因为 网络请求是非常耗电的。
	 * **/
	public static boolean autoReLogin = true;
	
	private static ClientCoreSDK instance = null;

	/** 核心框架是否已经初始化. **/
	private boolean _init = false;
	/** 是否已成功连接到服务器 **/
	private boolean connectedToServer = true;
	/** 用户是否已登陆 **/
	private boolean loginHasInit = false;
	/** 登陆人ID **/
	private String currentLoginUserId = null;
	/** 登陆人Token **/
	private String currentLoginToken = null;
	/** 登陆人额外信息 **/
	private String currentLoginExtra = null;

	/** 框架基础通信消息的回调事件通知实现对象 **/
	private ChatBaseEvent chatBaseEvent = null;
	/** 通用数据通信消息的回调事件通知实现对象 **/
	private ChatTransDataEvent chatTransDataEvent = null;
	/** QoS质量保证机制的回调事件通知实现对象 **/
	private MessageQoSEvent messageQoSEvent = null;

	/**
	 * 取得本类实例的唯一公开方法。
	 * @return
	 */
	public static ClientCoreSDK getInstance()
	{
		if(instance == null)
			instance = new ClientCoreSDK();
		return instance;
	}
	
	private ClientCoreSDK()
	{
	}

	/**
	 * 初始化核心库.
	 *
	 * 本方法被调用后， isInitialed()将返回true，否则返回false。
	 *
	 * 本方法无需调用者自行调用，它将在发送登陆路请求后（即调用 LocalUDPDataSender.SendLoginDataAsync 时）被自动调用。
	 */
	public void init()
	{
		if(!_init)
		{
			_init = true;
		}
	}

	/**
	 * 释放MobileIMSDK框架资源统一方法。
	 *
	 * 本方法建议在退出登陆（或退出APP时）时调用。调用时将尝试关闭所有 MobileIMSDK框架的后台守护线程并同设置核心框架init=false、 loginHasInit=false、connectedToServer=false。
	 * @{see net.openmob.mobileimsdk.java.core.AutoReLoginDaemon#stop()}
	 * @{see net.openmob.mobileimsdk.java.core.QoS4SendDaemon#stop()}
	 * @{see net.openmob.mobileimsdk.java.core.KeepAliveDaemon#stop()}
	 * @{see net.openmob.mobileimsdk.java.core.LocalUDPDataReciever#stop()}
	 * @{see net.openmob.mobileimsdk.java.core.QoS4ReciveDaemon#stop()}
	 * @{see net.openmob.mobileimsdk.java.core.LocalUDPSocketProvider#closeLocalUDPSocket()}
	 */
	public void release()
	{
		LocalUDPSocketProvider.getInstance().closeLocalUDPSocket();
	    AutoReLoginDaemon.getInstance().stop(); // 2014-11-08 add by Jack Jiang
		QoS4SendDaemon.getInstance().stop();
		KeepAliveDaemon.getInstance().stop();
		LocalUDPDataReciever.getInstance().stop();
		QoS4ReciveDaemon.getInstance().stop();
		
		//## Bug FIX: 20180103 by Jack Jiang START
		QoS4SendDaemon.getInstance().clear();
		QoS4ReciveDaemon.getInstance().clear();
		//## Bug FIX: 20180103 by Jack Jiang END
		
		_init = false;
		
		this.setLoginHasInit(false);
		this.setConnectedToServer(false);
	}

	/**
	 * 返回提交到服务端的准一用户id，保证唯一就可以通信，可能是登陆用户名 、也可能是任意不重复的id等，具体意义由业务层决定。
	 *
	 * 因不保证服务端正确收到和处理了该用户的登陆信息，所以本字段应只在 connectedToServer==true 时才有意义.
	 * @return
	 */
	public String getCurrentLoginUserId()
	{
		return currentLoginUserId;
	}

	/**
	 * 登陆信息成功发出后就会设置本字段（即登陆uid），登陆uid也将 在掉线后自动重连时使用。
	 *
	 * 本方法由框架自动调用，无需也不建议应用层调用。
	 * @param currentLoginUserId
	 * @return
	 */
	public ClientCoreSDK setCurrentLoginUserId(String currentLoginUserId)
	{
		this.currentLoginUserId = currentLoginUserId;
		return this;
	}

	/**
	 * 返回登陆信息成功发出后被设置的登陆token。
	 *
	 * 因不保证服务端正确收到和处理了该用户的登陆信息，所以本字段因只在 connectedToServer==true 时才有意义.
	 * @return
	 */
	public String getCurrentLoginToken()
	{
		return currentLoginToken;
	}

	/**
	 * 登陆信息成功发出后就会设置本字段（即登陆token），登陆token也将 在掉线后自动重连时使用。
	 *
	 * 本方法由框架自动调用，无需也不建议应用层调用。
	 * @param currentLoginToken
	 */
	public void setCurrentLoginToken(String currentLoginToken)
	{
		this.currentLoginToken = currentLoginToken;
	}

	/**
	 * 返回登陆信息成功发出后被设置的登陆额外信息（其是由调用者自行设置，不设置则为null）。
	 *
	 * 因不保证服务端正确收到和处理了该用户的登陆信息，所以本字段因只在 connectedToServer==true 时才有意义.
	 * @return
	 */
	public String getCurrentLoginExtra()
	{
		return currentLoginExtra;
	}

	/**
	 * 登陆信息成功发出后就会设置本字段（即登陆额外信息，其是由调用者自行设置，不设置则为null），登陆额外信息也将 在掉线后自动重连时使用。
	 *
	 * 本方法由框架自动调用，无需也不建议应用层调用。
	 * @param currentLoginExtra
	 * @return
	 */
	public ClientCoreSDK setCurrentLoginExtra(String currentLoginExtra)
	{
		this.currentLoginExtra = currentLoginExtra;
		return this;
	}

	/**
	 * 当且仅当用户从登陆界面成功登陆后设置本字段为true， 服务端反馈会话被注销或系统退出（登陆）时自动被设置为false。
	 *
	 * @return
	 */
	public boolean isLoginHasInit()
	{
		return loginHasInit;
	}

	/**
	 * 当且仅当用户从登陆界面成功登陆后设置本字段为true， 服务端反馈会话被注销或系统退出（登陆）时自动被设置为false。
	 *
	 * 本方法由框架自动调用，无需也不建议应用层调用。
	 * @param loginHasInit
	 * @return
	 */
	public ClientCoreSDK setLoginHasInit(boolean loginHasInit)
	{
		this.loginHasInit = loginHasInit;
		return this;
	}

	/**
	 * 是否已成功连接到服务器（当然，前提是已成功发起过登陆请求后）.
	 *
	 * 此“成功”意味着可以正常与服务端通信（可以近似理解为Socket正常建立） ，“不成功”意味着不能与服务端通信.
	 * 不成功的因素有很多：比如网络不可用、网络状况很差导致的掉线、心跳超时等.
	 *
	 * 本参数是整个MobileIMSDK框架中唯一可作为判断与MobileIMSDK服务器的通信是否正常的准确依据。
	 *
	 * 本参数将在收到服务端的登陆请求反馈后被设置为true，在与服务端的通信无法正常完成时被设置为false。
	 * 那么MobileIMSDK如何判断与服务端的通信是否正常呢？ 判断方法如下：
	 *
	 * 登陆请求被正常反馈即意味着通信正常（包括首次登陆时和断掉后的自动重新时）；
	 * 首次登陆或断线后自动重连时登陆请求被发出后，没有收到服务端反馈时即意味着不正常；
	 * 与服务端通信正常后，在规定的超时时间内没有收到心跳包的反馈后即意味着与服务端的通信又中断了（即所谓的掉线）。
	 * 返回:
	 * @return true表示与服务端真正的通信正常（即准确指明可正常进行消息交互，而不只是物理网络连接正常，因为物理连接正常 并不意味着服务端允许你合法的进行消息交互），否由表示不正常
	 */
	public boolean isConnectedToServer()
	{
		return connectedToServer;
	}

	/**
	 * 设置已成功连接到服务器（当然，前提是已成功发起过登陆请求后）.
	 *
	 * 本方法由框架自动调用，无需也不建议应用层调用。
	 * @param connectedToServer
	 */
	public void setConnectedToServer(boolean connectedToServer)
	{
		this.connectedToServer = connectedToServer;
	}

	/**
	 * MobileIMSDK_X的核心框架是否已经初始化.
	 *
	 * 当调用 init()方法后本字段将被置为true，调用release() 时将被重新置为false.
	 * 本参数由框架自动设置。
	 * @return
	 */
	public boolean isInitialed()
	{
		return this._init;
	}

	/**
	 * 设置框架基础通信消息的回调事件通知实现对象（可能的通知 有：登陆成功事件通知、掉线事件通知）
	 * @param chatBaseEvent 框架基础通信消息的回调事件通知实现对象引用
	 */
	public void setChatBaseEvent(ChatBaseEvent chatBaseEvent)
	{
		this.chatBaseEvent = chatBaseEvent;
	}

	/**
	 * 返回框架基础通信消息的回调事件通知实现对象（可能的通知 有：登陆成功事件通知、掉线事件通知）
	 * @return
	 */
	public ChatBaseEvent getChatBaseEvent()
	{
		return chatBaseEvent;
	}

	/**
	 * 设置通用数据通信消息的回调事件通知实现对象（可能的通知 有：收到聊天数据事件通知、服务端返回的错误信息事件通知等）。
	 * @param chatTransDataEvent 通用数据通信消息的回调事件通知实现对象引用
	 */
	public void setChatTransDataEvent(ChatTransDataEvent chatTransDataEvent)
	{
		this.chatTransDataEvent = chatTransDataEvent;
	}

	/**
	 * 返回通用数据通信消息的回调事件通知实现对象引用（可能的通知 有：收到聊天数据事件通知、服务端返回的错误信息事件通知等）。
	 * @return
	 */
	public ChatTransDataEvent getChatTransDataEvent()
	{
		return chatTransDataEvent;
	}

	/**
	 * 设置QoS质量保证机制的回调事件通知实现对象（可能的通知 有：消息未成功发送的通知、消息已被对方成功收到的通知等）。
	 * @param messageQoSEvent
	 */
	public void setMessageQoSEvent(MessageQoSEvent messageQoSEvent)
	{
		this.messageQoSEvent = messageQoSEvent;
	}

	/**
	 * 返回QoS质量保证机制的回调事件通知实现对象（可能的通知 有：消息未成功发送的通知、消息已被对方成功收到的通知等）。
	 * @return
	 */
	public MessageQoSEvent getMessageQoSEvent()
	{
		return messageQoSEvent;
	}
}
