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
 * KeepAliveDaemon.java at 2020-4-15 22:54:00, code by Jack Jiang.
 */
package net.openmob.mobileimsdk.java.core;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Observer;

import javax.swing.Timer;

import net.openmob.mobileimsdk.java.ClientCoreSDK;
import net.openmob.mobileimsdk.java.utils.Log;

/**
 * 用于保持与服务端通信活性的Keep alive独立线程。
 *
 * Keep alive的目的有2个：
 * 1、防止NAT路由算法导致的端口老化：
 * 路由器的NAT路由算法存在所谓的“端口老化”概念 （理论上NAT算法中UDP端口老化时间为300S，但这不是标准，而且中高端路由器 可由网络管理员自行设定此值），
 * Keep alive机制可确保在端口老化时间到来前 重置老化时间，进而实现端口“保活”的目的，否则端口老化导致的后果是服务器 将向客户端发送的数据将被路由器抛弃。
 * 2、即时探测由于网络状态的变动而导致的通信中断（进而自动触发自动治愈机制）：
 * 此种情况可的原因有（但不限于）：无线网络信号不稳定、WiFi与2G/3G/4G等同开情 况下的网络切换、手机系统的省电策略等。
 *
 * 本线程的启停，目前属于MobileIMSDK算法的一部分，暂时无需也不建议由应用层自行调用。
 */
public class KeepAliveDaemon
{
	private final static String TAG = KeepAliveDaemon.class.getSimpleName();
	
	private static KeepAliveDaemon instance = null;

	/** 收到服务端响应心跳包的超时间时间（单位：毫秒），默认（3000 * 3 + 1000）＝ 10000 毫秒. **/
	public static int NETWORK_CONNECTION_TIME_OUT = 10 * 1000;
	/** Keep Alive 心跳时间间隔（单位：毫秒），默认3000毫秒. **/
	public static int KEEP_ALIVE_INTERVAL = 3000;//1000;
	
	private boolean keepAliveRunning = false;
	private long lastGetKeepAliveResponseFromServerTimstamp = 0;
	private Observer networkConnectionLostObserver = null;
	private boolean _excuting = false;
	private Timer timer = null;
	
	public static KeepAliveDaemon getInstance()
	{
		if(instance == null)
			instance = new KeepAliveDaemon();
		return instance;
	}
	
	private KeepAliveDaemon()
	{
		init();
	}
	
	private void init()
	{
		timer = new Timer(KEEP_ALIVE_INTERVAL, new ActionListener(){
			public void actionPerformed(ActionEvent e)
			{
				run();
			}
		});
	}
	
	public void run()
	{
		if(!_excuting)
		{
			boolean willStop = false;
			_excuting = true;
			if(ClientCoreSDK.DEBUG)
				Log.i(TAG, "【IMCORE】心跳线程执行中...");
			int code = LocalUDPDataSender.getInstance().sendKeepAlive();

			boolean isInitialedForKeepAlive = (lastGetKeepAliveResponseFromServerTimstamp == 0);
			if(isInitialedForKeepAlive)
				lastGetKeepAliveResponseFromServerTimstamp = System.currentTimeMillis();

			if(!isInitialedForKeepAlive)
			{
				long now = System.currentTimeMillis();
				if(now - lastGetKeepAliveResponseFromServerTimstamp >= NETWORK_CONNECTION_TIME_OUT)
				{
					stop();
					if(networkConnectionLostObserver != null)
						networkConnectionLostObserver.update(null, null);
					willStop = true;
				}
			}

			_excuting = false;
			if(!willStop)
			{
				; // do nothing
			}
			else
			{
				timer.stop();
			}
		}
	}
	
	public void stop()
	{
		if(timer != null)
			timer.stop();
		keepAliveRunning = false;
		lastGetKeepAliveResponseFromServerTimstamp = 0;
	}
	
	public void start(boolean immediately)
	{
		stop();
		
		if(immediately)
			timer.setInitialDelay(0);
		else
			timer.setInitialDelay(KEEP_ALIVE_INTERVAL);
		timer.start();
		
		keepAliveRunning = true;
	}

	/**
	 * 线程是否正在运行中。
	 * @return
	 */
	public boolean isKeepAliveRunning()
	{
		return keepAliveRunning;
	}

	/**
	 * 收到服务端反馈的心跳包时调用此方法：作用是更新服务端最背后的响应时间戳.
	 */
	public void updateGetKeepAliveResponseFromServerTimstamp()
	{
		lastGetKeepAliveResponseFromServerTimstamp = System.currentTimeMillis();
	}

	/**
	 * 设置网络断开事件观察者.
	 * @param networkConnectionLostObserver
	 */
	public void setNetworkConnectionLostObserver(Observer networkConnectionLostObserver)
	{
		this.networkConnectionLostObserver = networkConnectionLostObserver;
	}
}
