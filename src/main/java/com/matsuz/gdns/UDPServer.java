package com.matsuz.gdns;

import java.net.SocketAddress;
import java.net.InetSocketAddress;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;


public class UDPServer {
    private static int port = 53;

    public static void setPort(int port) {
        UDPServer.port = port;
    }

    public static void run() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
        	SocketAddress localAddrs= new InetSocketAddress("0.0.0.0",port);
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new UDPServerHandler());

            System.out.println("Listening on port " + port + "...");
     //       b.bind(port).sync().channel().closeFuture().await();
            b.bind(localAddrs).sync().channel().closeFuture().await();
        } finally {
            group.shutdownGracefully();
        }
    }
}
