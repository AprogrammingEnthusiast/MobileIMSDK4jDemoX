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
 * LocalUDPDataSender.java at 2020-4-15 22:53:59, code by Jack Jiang.
 */
package net.openmob.mobileimsdk.java.core;

import java.net.DatagramSocket;
import java.net.InetAddress;

import net.openmob.mobileimsdk.java.ClientCoreSDK;
import net.openmob.mobileimsdk.java.conf.ConfigEntity;
import net.openmob.mobileimsdk.java.utils.Log;
import net.openmob.mobileimsdk.java.utils.UDPUtils;

import org.jdesktop.swingworker.SwingWorker;

import net.openmob.mobileimsdk.server.protocal.ProtocalFactory;
import net.openmob.mobileimsdk.server.protocal.ErrorCode;
import net.openmob.mobileimsdk.server.protocal.Protocal;

/**
 * 数据发送处理实用类。
 */
public class LocalUDPDataSender
{
	private final static String TAG = LocalUDPDataSender.class.getSimpleName();
	
	private static LocalUDPDataSender instance = null;
	
	public static LocalUDPDataSender getInstance()
	{
		if(instance == null)
			instance = new LocalUDPDataSender();
		return instance;
	}
	
	private LocalUDPDataSender()
	{
	}

	/**
	 * 异步发送登陆请求
	 * @param loginUserId
	 * @param loginToken
	 * @param extra
	 * @return
	 */
	int sendLogin(String loginUserId, String loginToken, String extra)
	{
		//调用服务端协议定义类，封装统一协议解析后字节
		byte[] b = ProtocalFactory.createPLoginInfo(loginUserId, loginToken, extra).toBytes();
		//发送至服务端
		int code = send(b, b.length);
		//成功后则返回用户登陆信息，并保存至ClientCoreSDK中
		if(code == 0)
		{
			ClientCoreSDK.getInstance().setCurrentLoginUserId(loginUserId);
			ClientCoreSDK.getInstance().setCurrentLoginToken(loginToken);
			ClientCoreSDK.getInstance().setCurrentLoginExtra(extra);
		}
		
		return code;
	}
	
	public int sendLoginout()
	{
		int code = ErrorCode.COMMON_CODE_OK;
		if(ClientCoreSDK.getInstance().isLoginHasInit())
		{
			byte[] b = ProtocalFactory.createPLoginoutInfo(
					ClientCoreSDK.getInstance().getCurrentLoginUserId()).toBytes();
			code = send(b, b.length);
			if(code == 0)
			{
				// do nothing
			}
		}
		ClientCoreSDK.getInstance().release();
		return code;
	}
	
	int sendKeepAlive()
	{
		byte[] b = ProtocalFactory.createPKeepAlive(
				ClientCoreSDK.getInstance().getCurrentLoginUserId()).toBytes();
		return send(b, b.length);
	}
		
	public int sendCommonData(String dataContentWidthStr, String to_user_id)
	{
		return sendCommonData(dataContentWidthStr, to_user_id, -1);
	}
	public int sendCommonData(String dataContentWidthStr, String to_user_id, int typeu)
	{
		return sendCommonData(dataContentWidthStr, to_user_id, null, typeu);
	}
	public int sendCommonData(String dataContentWidthStr, String to_user_id
			, String fingerPrint, int typeu)
	{
		return sendCommonData(dataContentWidthStr, to_user_id, true, fingerPrint, typeu);
	}
	public int sendCommonData(String dataContentWidthStr, String to_user_id
			, boolean QoS, String fingerPrint, int typeu)
	{
		return sendCommonData(ProtocalFactory.createCommonData(dataContentWidthStr
				, ClientCoreSDK.getInstance().getCurrentLoginUserId(), to_user_id, QoS, fingerPrint, typeu));
	}
	
	public int sendCommonData(Protocal p)
	{
		if(p != null)
		{
			byte[] b = p.toBytes();
			int code = send(b, b.length);
			if(code == 0)
			{
				if(p.isQoS() && !QoS4SendDaemon.getInstance().exist(p.getFp()))
					QoS4SendDaemon.getInstance().put(p);
			}
			return code;
		}
		else
			return ErrorCode.COMMON_INVALID_PROTOCAL;
	}

	/**
	 * 发送数据
	 * @param fullProtocalBytes
	 * @param dataLen
	 * @return
	 */
	private int send(byte[] fullProtocalBytes, int dataLen)
	{
		if(!ClientCoreSDK.getInstance().isInitialed())
			return ErrorCode.ForC.CLIENT_SDK_NO_INITIALED;

		//获取本地 UDP Socket 实例
		DatagramSocket ds = LocalUDPSocketProvider.getInstance().getLocalUDPSocket();
		if(ds != null && !ds.isConnected())
		{
			try
			{
				if(ConfigEntity.serverIP == null)
				{
					Log.w(TAG, "【IMCORE】send数据没有继续，原因是ConfigEntity.server_ip==null!");
					return ErrorCode.ForC.TO_SERVER_NET_INFO_NOT_SETUP;
				}
				//通过UDP链接服务器
				ds.connect(InetAddress.getByName(ConfigEntity.serverIP), ConfigEntity.serverUDPPort);
			}
			catch (Exception e)
			{
				Log.w(TAG, "【IMCORE】send时出错，原因是："+e.getMessage(), e);
				return ErrorCode.ForC.BAD_CONNECT_TO_SERVER;
			}
		}
		//发送信息
		return UDPUtils.send(ds, fullProtocalBytes, dataLen) ? ErrorCode.COMMON_CODE_OK : ErrorCode.COMMON_DATA_SEND_FAILD;
	}

	/**
	 * 异步发送数据公共模版
	 */
	public static abstract class SendCommonDataAsync extends SwingWorker<Integer, Object>
	{
		protected Protocal p = null;
		
		public SendCommonDataAsync(String dataContentWidthStr, String to_user_id)
		{
			this(dataContentWidthStr, to_user_id, null, -1);
		}
		public SendCommonDataAsync(String dataContentWidthStr, String to_user_id
				, int typeu)
		{
			this(dataContentWidthStr, to_user_id, null, typeu);
		}
		public SendCommonDataAsync(String dataContentWidthStr, String to_user_id
				, String fingerPrint, int typeu)
		{
			this(ProtocalFactory.createCommonData(dataContentWidthStr
					, ClientCoreSDK.getInstance().getCurrentLoginUserId(), to_user_id
					, true, fingerPrint, typeu));
		}

		public SendCommonDataAsync(Protocal p)
		{
			if(p == null)
			{
				Log.w(TAG, "【IMCORE】无效的参数p==null!");
				return;
			}
			this.p = p;
		}

		@Override
		protected Integer doInBackground()
		{
			if(p != null)
				return LocalUDPDataSender.getInstance().sendCommonData(p);//dataContentWidthStr, to_user_id);
			return -1;
		}
		
		@Override
		protected void done()
		{
			int code = -1;
			try
			{
				code = get();
			}
			catch (Exception e)
			{
				Log.w(TAG, e.getMessage());
			}
			
			onPostExecute(code);
		}

		protected abstract void onPostExecute(Integer code);
	}

	/**
	 * 异步发送登陆信息
	 */
	public static class SendLoginDataAsync extends SwingWorker<Integer, Object>
	{
		protected String loginUserId = null;
		protected String loginToken = null;
		protected String extra = null;

		public SendLoginDataAsync(String loginUserId, String loginToken)
		{
			this(loginUserId, loginToken, null);
		}

		public SendLoginDataAsync(String loginUserId, String loginToken, String extra)
		{
			this.loginUserId = loginUserId;
			this.loginToken = loginToken;
			this.extra = extra;
			
			ClientCoreSDK.getInstance().init();
		}

		/**
		 * 异步发起发送登陆请求
		 * @return
		 */
		@Override
		protected Integer doInBackground()
		{
			int code = LocalUDPDataSender.getInstance().sendLogin(
					this.loginUserId, this.loginToken, this.extra);
			return code;
		}

		/**
		 * swing回调，登陆完成后出发
		 */
		@Override
		protected void done()
		{
			int code = -1;
			try
			{
				code = get();
			}
			catch (Exception e)
			{
				Log.w(TAG, e.getMessage());
			}
			
			onPostExecute(code);
		}

		/**
		 * 启动数据接受处理线程
		 * @param code
		 */
		protected void onPostExecute(Integer code)
		{
			if(code == 0)
			{
				//启动数据接收处理独立线程。
				LocalUDPDataReciever.getInstance().startup();
			}
			else
			{
				Log.d(TAG, "【IMCORE】数据发送失败, 错误码是："+code+"！");
			}

			//登陆完成后的回调方法
			fireAfterSendLogin(code);
		}

		/**
		 * 登陆后回调实现
		 * @{see net.openmob.mobileimsdk.java.demo.LoginGUI#doLoginImpl()}
		 * @param code
		 */
		protected void fireAfterSendLogin(int code)
		{
			// default do nothing
		}
	}
}
