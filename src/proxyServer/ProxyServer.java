package proxyServer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;


public class ProxyServer
{

	private Set<String> forbidSite = new HashSet<>(); // 不允许访问的网站
	private Set<String> forbidUser = new HashSet<>(); // 不允许访问的用户
	private Map<String, String> redirectHost = new HashMap<>();	//重定向主机
	private Map<String, String> redirectUrl = new HashMap<>();	//重定向url

	/**
	 * 解析 TCP 报文中的 HTTP 头部
	 * 
	 * @param header HTTP头部
	 * @return
	 */
	public Map<String, String> parseHttpHeader(String header)
	{
		if (header.length() == 0)
			return new HashMap<>();
		String[] lines = header.split("\\n");
		String method = null;
		String url = null;
		String host = null;
		String port = null;
		String temp[] = null;
		for (String s : lines)
		{
			if (method == null && (s.contains("GET") || s.contains("POST") || s.contains("CONNECT")))
			{
				temp = s.split("\\s");
				method = temp[0];
				url = temp[1];
				if (url.contains("http://") || url.contains("https://"))
				{
					temp = url.split(":");
					if (temp.length >= 3)
						port = temp[2];
				} else
				{
					temp = url.split(":");
					if (temp.length >= 2)
						port = temp[1];
				}
				if (port!=null && port.contains("/"))
					port = port.split("/")[0];
			} else if ((s.contains("Host:") || s.contains("HOSt")) && host == null)
			{
				temp = s.split("\\s");
				host = temp[1];
				int colon = host.indexOf(':');
				if (colon != -1)
				{
					String tempHost=host;
					host = tempHost.substring(0, colon);
					if (port == null)
						port = tempHost.substring(colon + 1, tempHost.length());
				}
			}
		}
		// if (!url.contains("http"))
		// {
		// 	url = host + url;
		// }
		Map<String, String> ret = new HashMap<>();
		ret.put("method", method);
		ret.put("url", url);
		ret.put("host", host);
		if (port == null)
			ret.put("port", "80");
		else
			ret.put("port", port);
		return ret;
	}

	/**
	 * 添加禁止访问的网站
	 * @param url	被禁止访问的网站的url
	 * @return	添加成功返回true，若禁止名单里已包含给定url，返回false
	 */
	public boolean addForbidSite(String url)
	{
		return forbidSite.add(url);
	}

	/**
	 * 添加禁止访问外网的用户
	 * @param host	被禁止用户的主机名
	 * @return	添加成功返回true，若禁止名单里已包含给定用户，返回false
	 */
	public boolean addForbidUser(String host)
	{
		return forbidUser.add(host);
	}

	/**
	 * 添加原主机和重定向后的主机
	 * @param originHost	原主机
	 * @param newHost	重定向后的主机
	 * @return	添加成功返回与原主机匹配的上一个重定向主机，如果之前原主机没有被重定向，返回null
	 */
	public String addRedirectHost(String originHost,String newHost)
	{
		return redirectHost.put(originHost, newHost);
	}

	/**
	 * 重定向主机
	 * @param host 原主机名
	 * @return	如果需要重定向，则返回重定向后的主机名，否则返回null
	 */
	public String redirectHost(String host)
	{
		Set<String> hostSet=redirectHost.keySet();
		for(String s : hostSet)
		{
			if(s.equals(host))
			{
				String newHost=redirectHost.get(host);
				System.out.println("原主机 "+host+" 被重定向到"+newHost);
				return newHost;
			}
		}
		return null;
	}

	/**
	 * 添加原url和重定向后的url
	 * @param originUrl	原url
	 * @param newUrl	重定向后的url
	 * @return	添加成功返回与原url匹配的上一个重定向url，如果之前原url没有被重定向，返回null
	 */
	public String addRedirectUrl(String originUrl,String newUrl)
	{
		return redirectUrl.put(originUrl, newUrl);
	}

	/**
	 * 重定向url
	 * @param url	原url
	 * @return	如果需要重定向，返回重定向后的url，否则返回null
	 */
	public String redirectUrl(String url)
	{
		Set<String> urlSet=redirectUrl.keySet();
		for(String s : urlSet)
		{
			if(s.equals(url))
			{
				String newUrl=redirectUrl.get(url);
				System.out.println("原url "+url+" 被重定向到"+newUrl);
				return newUrl;
			}
		}
		return null;
	}

	/**
	 * 
	 * @param header
	 * @return
	 */
	public String rebuildHttpHead(String header,String newHost,String newUrl)
	{
		StringBuilder sb=new StringBuilder();
		String []lines=header.split("\\n");
		String[] temp=null;
		for(String s: lines)
		{
			if(s.contains("GET") || s.contains("POST") || s.contains("CONNECT"))
			{
				temp = s.split("\\s");  // 按空格分割
                sb.append(temp[0] + " " + newUrl + " " + temp[2] + "\n");
			}
			else if(s.contains("Host: "))
			{
				sb.append("Host: "+newHost+"\n");
			}
			else if(s.contains("Referer: "))
			{
				sb.append("Referer: "+newUrl+"\n");
			}
			else if (s.contains("Accept: ")) 
			{
                sb.append("Accept: " + "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8" + "\n");
            }
			else
			{
				sb.append(s+"\n");
			}
		}
		return sb.toString();
	}

	/**
	 * 
	 * @param localPort
	 */
	public void start(int localPort) throws IOException
	{
		ServerSocket server=new ServerSocket(localPort);
		System.out.println("代理服务器正在运行，监听端口 "+localPort);
		while(true)
		{
			Socket socket=server.accept();
			String clientIP=socket.getInetAddress().getHostAddress();
			System.out.println("与主机 "+clientIP+" 连接成功");
			if(forbidUser.contains(clientIP))
			{
				System.out.println("用户"+clientIP+"被禁止访问外部网站");
				PrintWriter pw=new PrintWriter(socket.getOutputStream());
				pw.println("Forbidden user!\n");
				pw.close();
				socket.close();
				continue;
			}
			new Thread(new Runnable() {
				@Override
				public void run()
				{
					try
					{
						BufferedReader br=new BufferedReader(
							new InputStreamReader(socket.getInputStream()));
						String line=br.readLine();
						StringBuilder headerBuilder=new StringBuilder();
						while(line!=null && !line.equals(""))
						{
							headerBuilder.append(line).append("\n");
							line=br.readLine();
						}
						String header=headerBuilder.toString();
						System.out.println("\n"+header+"\n");
						Map<String,String> headInfo=parseHttpHeader(header);
						String method=headInfo.getOrDefault("method","GET");
						String url=headInfo.get("url");
						String host=headInfo.get("host");
						int port=Integer.parseInt(headInfo.getOrDefault("port","80"));
						if(url!=null && forbidSite.contains(url))
						{
							System.out.println("访问了禁止访问的网站："+url);
							PrintWriter pw=new PrintWriter(socket.getOutputStream());
							pw.println("The website "+url+" is forbidden visiting!");
							pw.close();
							socket.close();
							return;
						}
						String tempHost=redirectHost(host);
						if(tempHost!=null)
						{
							host=tempHost;
							url=redirectUrl(url);
							//header=rebuildHttpHead(header, host, url);
						}
						File cacheFile=new File(url.replace('/', '_').replace(':', '+')+".cache");
						boolean useCache=false;
						String lastModified="Thu, 01 Jul 1970 20:00:00 GMT";
						if(cacheFile.exists() && cacheFile.length()!=0)
						{
							System.out.println(url+"有本地缓存");
							long time = cacheFile.lastModified();
							SimpleDateFormat formatter=
							new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
							Calendar cal=Calendar.getInstance();
							cal.setTimeInMillis(time);
							cal.set(Calendar.HOUR, -7);
							cal.setTimeZone(TimeZone.getTimeZone("GMT"));
							lastModified=formatter.format(cal.getTime());
							System.out.println("缓存最后修改时间："+cal.getTime());
						}
						Socket remoteServer=new Socket(host,port);
						BufferedWriter writer=new BufferedWriter(
							new OutputStreamWriter(remoteServer.getOutputStream()));
						StringBuffer request=new StringBuffer();
						request//.append(method).append(" ").append(url)
//                        .append(" HTTP/1.1").append("\n")
//                        .append("HOST: ").append(host).append("\n")
//                        .append("Accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\n")
//                        .append("Accept-Encoding:gzip, deflate, sdch\n")
//                        .append("Accept-Language:zh-CN,zh;q=0.8\n")
//                        .append("If-Modified-Since: ").append(lastModified).append("\n")
//                        //.append("User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.2 Safari/605.1.15\n")
//                        .append("Encoding:UTF-8\n")
//                        .append("Connection:keep-alive" + "\n")
//                        .append("\n");
						 .append(method+" "+url+" HTTP/1.1\n")
						 	.append("HOST: "+host+"\n")
						 	.append("Accept:text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\n")
						 	.append("Accept-Encoding:gzip, deflate, sdch\n")
						 	.append("Accept-Language:zh-CN,zh;q=0.8\n")
						 	.append("If-Modified-Since: "+lastModified+"\n")
						 	.append("Encoding:UTF-8\n")
						 	.append("Connection:keep-alive\n\n");
						writer.write(request.toString());
						System.out.println(request.toString());
						writer.flush();
						
						//向浏览器的输出流
						OutputStream outToBrowser=socket.getOutputStream();
						//向缓存文件的输出流
						FileOutputStream fileOutputStream=new FileOutputStream(
							new File(url.replace('/', '_').replace(':', '+')+".cache"));
	
						//来自远程服务器的输入流
						BufferedInputStream remoteInput=new BufferedInputStream(remoteServer.getInputStream());
						byte[] tempBytes=new byte[20];
						int bytes=remoteInput.read(tempBytes);
						String recv=new String(tempBytes,0,bytes);
						System.out.println(recv);
						//判断响应报文里是否包含304，若包含，标记为使用缓存
						if(recv.contains("304"))
						{
							System.out.println(url+" 服务器端内容未更新，使用缓存");
							useCache=true;
						}
						else
						{
							System.out.println(url+" 服务器端内容发生更新，不使用缓存");
							outToBrowser.write(tempBytes);
							fileOutputStream.write(tempBytes);
						}
						if(useCache)
						{
							System.out.println(url+" 正在使用缓存加载");
							FileInputStream fileInputStream=new FileInputStream(cacheFile);
							int bufferLength=1;
							byte[] buffer=new byte[bufferLength];
							int count=0;
							while(true)
							{
								count=fileInputStream.read(buffer);
								System.out.println("Reading "+count+" bytes from cache file");
								if(count==-1)
								{
									break;
								}
								outToBrowser.write(buffer);
							}
							outToBrowser.flush();
						}
						//把来自服务器的输入流读完，
						int bufferLength=1;
						byte[] buffer=new byte[bufferLength];
						int count=0;
						System.out.println("Reading from "+url);
						while(true)
						{
							count=remoteInput.read(buffer);
							if(count==-1)
							{
								break;
							}
							if(!useCache)
							{
								outToBrowser.write(buffer);
								fileOutputStream.write(buffer);
							}
						}
						fileOutputStream.flush();
						fileOutputStream.close();
						outToBrowser.flush();
						remoteServer.close();
						socket.close();
					} catch (Exception e)
					{
						// TODO: handle exception
					}
				}
			
			}).start();
			
		 }
		 
	}

	public static void main(String[] args) throws IOException 
	{
		// TODO Auto-generated method stub
		ProxyServer proxyServer=new ProxyServer();
//		proxyServer.addForbidUser("127.0.0.1");
//		proxyServer.addForbidSite("jwes.hit.edu.cn");
//		proxyServer.addForbidSite("jwes.hit.edu.cn/");
//		proxyServer.addForbidSite("http://jwes.hit.edu.cn");
//		proxyServer.addForbidSite("http://jwes.hit.edu.cn/");
//		proxyServer.addRedirectHost("jwts.hit.edu.cn", "www.ipuhui.com");
//		proxyServer.addRedirectUrl("http://jwts.hit.edu.cn/", "http://www.ipuhui.com/");
//		proxyServer.addRedirectUrl("http://jwts.hit.edu.cn", "http://www.ipuhui.com/");
		proxyServer.start(808);
	}

}
