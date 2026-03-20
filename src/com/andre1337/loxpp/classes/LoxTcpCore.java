package com.andre1337.loxpp.classes;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
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

    public static CompletableFuture<ServerSocket> ___tcp_bind_s___(int port, String keystorePath, String keystorePass) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(new FileInputStream(keystorePath), keystorePass.toCharArray());

                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
                keyManagerFactory.init(keyStore, keystorePass.toCharArray());

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

                SSLServerSocketFactory socketFactory = sslContext.getServerSocketFactory();
                return socketFactory.createServerSocket(port);
            } catch (Exception e) {
                System.err.println("FATAL SSL BIND ERROR: " + e.getMessage());
                return null;
            }
        });
    }

    public static CompletableFuture<Object> ___tcp_accept___(Object serverArg) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (serverArg instanceof javax.net.ssl.SSLServerSocket sslServer) {
                    return sslServer.accept();
                }

                return ((AsynchronousServerSocketChannel) serverArg).accept().get();
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

    public static CompletableFuture<LoxString> ___tcp_read___(Object socket, Object bufferSize) {
        int bSize = (int)(double) bufferSize;

        if (socket instanceof javax.net.ssl.SSLSocket sslClient) {
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    java.io.InputStream is = sslClient.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[bSize];

                    int bytesRead = is.read(buffer);
                    if (bytesRead == -1) return null;

                    baos.write(buffer, 0, bytesRead);
                    String currentData = baos.toString(StandardCharsets.ISO_8859_1);

                    // If there is a body, calculate the size and loop until we have it all!
                    if (currentData.contains("\r\n\r\n") && currentData.contains("Content-Length: ")) {
                        try {
                            int lengthIndex = currentData.indexOf("Content-Length: ") + 16;
                            int endOfLine = currentData.indexOf("\r\n", lengthIndex);
                            int contentLength = Integer.parseInt(currentData.substring(lengthIndex, endOfLine).trim());

                            int headerLength = currentData.indexOf("\r\n\r\n") + 4;
                            int totalExpectedBytes = headerLength + contentLength;

                            while (baos.size() < totalExpectedBytes) {
                                bytesRead = is.read(buffer);
                                if (bytesRead == -1) break;
                                baos.write(buffer, 0, bytesRead);
                            }
                        } catch (Exception e) {
                            System.out.println("JAVA SSL Error parsing Content-Length: " + e.getMessage());
                        }
                    }

                    return new LoxString(baos.toString(StandardCharsets.ISO_8859_1));
                } catch (Exception e) {
                    return null;
                }
            });
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                AsynchronousSocketChannel sckt = (AsynchronousSocketChannel) socket;

                if (sckt == null) {
                    System.out.println("JAVA: socket is null.");
                    return null;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ByteBuffer buffer = ByteBuffer.allocate(bSize);
                Integer bytesRead = sckt.read(buffer).get();

                if (bytesRead == null || bytesRead == -1) {
                    return null;
                }

                buffer.flip();
                byte[] firstChunk = new byte[bytesRead];
                buffer.get(firstChunk);
                baos.write(firstChunk);

                String currentData = new String(firstChunk, StandardCharsets.ISO_8859_1);

                if (currentData.contains("\r\n\r\n") && currentData.contains("Content-Length: ")) {
                    try {
                        int lengthIndex = currentData.indexOf("Content-Length: ") + 16;
                        int endOfLine = currentData.indexOf("\r\n", lengthIndex);
                        int contentLength = Integer.parseInt(currentData.substring(lengthIndex, endOfLine).trim());

                        int headerLength = currentData.indexOf("\r\n\r\n") + 4;
                        int totalExpectedBytes = headerLength + contentLength;

                        while (baos.size() < totalExpectedBytes) {
                            buffer.clear();
                            Integer nextBytesRead = sckt.read(buffer).get();

                            if (nextBytesRead == null || nextBytesRead == -1) break;

                            buffer.flip();
                            byte[] nextChunk = new byte[nextBytesRead];
                            buffer.get(nextChunk);
                            baos.write(nextChunk);
                        }
                    } catch (Exception e) {
                        System.out.println("JAVA: Error parsing Content-Length: " + e.getMessage());
                    }
                }

                return new LoxString(baos.toString(StandardCharsets.ISO_8859_1));
            } catch (Exception e) {
                System.out.println("JAVA: connection interrupted by browser:" +
                        (e.getCause() != null ? e.getCause().getClass().getSimpleName() : e.getMessage()));
                return null;
            }
        });
    }

    public static CompletableFuture<Boolean> ___tcp_write___(Object sckt, Object dataObj) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        String data = dataObj instanceof LoxString loxStr ? loxStr.value : dataObj.toString();
        byte[] bytes = data.getBytes(StandardCharsets.ISO_8859_1);

        if (sckt instanceof SSLSocket sslSocket) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    sslSocket.getOutputStream().write(bytes);
                    sslSocket.getOutputStream().flush();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
        }

        AsynchronousSocketChannel socket = (AsynchronousSocketChannel) sckt;

        if (socket == null || !socket.isOpen()) {
            future.complete(false);
            return future;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

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

    public static CompletableFuture<Boolean> ___tcp_close___(Object sckt) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (sckt instanceof SSLSocket sslClient) {
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { sslClient.close(); return true; } catch (Exception e) { return false; }
            });
        }

        AsynchronousSocketChannel socket = (AsynchronousSocketChannel) sckt;

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