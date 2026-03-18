package com.andre1337.loxpp.classes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class LoxTcpCore {
    public static CompletableFuture<AsynchronousServerSocketChannel> ___tcp_bind___(int port) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
                server.bind(new InetSocketAddress(port));
                return server;
            } catch (IOException e) {
                throw new RuntimeException("Error binding server to port " + port, e);
            }
        });
    }

    public static CompletableFuture<AsynchronousSocketChannel> ___tcp_accept___(AsynchronousServerSocketChannel server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return server.accept().get();
            } catch (Exception e) {
                throw new RuntimeException("Error accepting connection", e);
            }
        });
    }

    public static CompletableFuture<Void> ___tcp_server_close___(AsynchronousServerSocketChannel server) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (server != null && server.isOpen()) server.close();
            } catch (IOException e) {
                throw new RuntimeException("Error closing server", e);
            }
        });
    }

    public static CompletableFuture<LoxString> ___tcp_read___(AsynchronousSocketChannel socket, int bufferSize) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (socket == null) {
                    System.out.println("JAVA: socket is null.");
                    return null;
                }

                ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
                Integer bytesRead = socket.read(buffer).get();

                if (bytesRead == null || bytesRead == -1) {
                    return null;
                }

                buffer.flip();
                byte[] data = new byte[bytesRead];
                buffer.get(data);

                return new LoxString(new String(data));
            } catch (Exception e) {
                System.out.println("JAVA: connection interrupted by browser:" +
                        (e.getCause() != null ? e.getCause().getClass().getSimpleName() : e.getMessage()));
                return null;
            }
        });
    }

    public static CompletableFuture<Boolean> ___tcp_write___(AsynchronousSocketChannel socket, Object dataObj) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (socket == null || !socket.isOpen()) {
            future.complete(false);
            return future;
        }

        String data = dataObj instanceof LoxString loxStr ? loxStr.value : dataObj.toString();
        ByteBuffer buffer = ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8));

        socket.write(buffer, null, new java.nio.channels.CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                future.complete(true);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                System.out.println("JAVA ERROR: ___tcp_write___: " + exc.getMessage());
            }
        });

        return future;
    }

    public static CompletableFuture<Boolean> ___tcp_close___(AsynchronousSocketChannel socket) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            if (socket != null && socket.isOpen()) socket.close();
            future.complete(true);
        } catch (Exception e) {
            System.out.println("JAVA ERROR: ___tcp_close___: " + e.getMessage());
            future.complete(false);
        }

        return future;
    }
}