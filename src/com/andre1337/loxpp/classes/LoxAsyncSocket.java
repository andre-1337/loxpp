package com.andre1337.loxpp.classes;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LoxAsyncSocket {

    // < client methods
    public static CompletableFuture<AsynchronousSocketChannel> ___connect___(String host, int port) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AsynchronousSocketChannel socket = AsynchronousSocketChannel.open();
                socket.connect(new InetSocketAddress(host, port)).get();
                return socket;
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect: " + e.getMessage());
            }
        });
    }

    public static CompletableFuture<Void> ___send___(AsynchronousSocketChannel socket, String data) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(data.getBytes());
                socket.write(buffer).get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to send data: " + e.getMessage());
            }
        });
    }

    public static CompletableFuture<String> ___receive___(AsynchronousSocketChannel socket, int bufferSize) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
                socket.read(buffer).get();
                buffer.flip();
                return new String(buffer.array(), 0, buffer.limit());
            } catch (Exception e) {
                throw new RuntimeException("Failed to receive data: " + e.getMessage());
            }
        });
    }

    public static CompletableFuture<Void> ___close___(AsynchronousSocketChannel socket) {
        return CompletableFuture.runAsync(() -> {
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close socket: " + e.getMessage());
            }
        });
    }
    // client methods >

    // < server methods
    public static CompletableFuture<AsynchronousServerSocketChannel> ___bind_and_listen___(int port) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
            } catch (IOException e) {
                throw new RuntimeException("Failed to bind server socket: " + e.getMessage());
            }
        });
    }

    public static CompletableFuture<AsynchronousSocketChannel> ___accept___(AsynchronousServerSocketChannel serverSocket) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return serverSocket.accept().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to accept client connection: " + e.getMessage());
            }
        });
    }

    public static CompletableFuture<Void> ___close_server___(AsynchronousServerSocketChannel serverSocket) {
        return CompletableFuture.runAsync(() -> {
            try {
                serverSocket.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close server socket: " + e.getMessage());
            }
        });
    }
    // server methods >
}