package org.eclipse.jetty.websocket.client.io;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.AbstractAsyncConnection;
import org.eclipse.jetty.io.AsyncConnection;
import org.eclipse.jetty.io.AsyncEndPoint;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.QuotedStringTokenizer;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.client.WebSocketClient.ConnectFuture;
import org.eclipse.jetty.websocket.io.WebSocketAsyncConnection;

/**
 * Default Handshake Connection.
 * <p>
 * Results in a {@link WebSocketAsyncConnection} on successful handshake.
 */
public class HandshakeConnection extends AbstractAsyncConnection implements AsyncConnection
{
    public static final String COOKIE_DELIM = "\"\\\n\r\t\f\b%+ ;=";
    private final WebSocketClient.ConnectFuture future;
    private final ByteBufferPool bufferPool;

    private String key;

    public HandshakeConnection(AsyncEndPoint endp, Executor executor, ByteBufferPool bufferPool, WebSocketClient.ConnectFuture future)
    {
        super(endp,executor);
        this.future = future;
        this.bufferPool = bufferPool;

        byte[] bytes = new byte[16];
        new Random().nextBytes(bytes);
        this.key = new String(B64Code.encode(bytes));
    }

    public void handshake() throws InterruptedException, ExecutionException
    {
        URI uri = future.getWebSocketUri();

        StringBuilder request = new StringBuilder(512);
        request.append("GET ");
        if (StringUtil.isBlank(uri.getPath()))
        {
            request.append("/");
        }
        else
        {
            request.append(uri.getPath());
        }
        if (StringUtil.isNotBlank(uri.getRawQuery()))
        {
            request.append("?").append(uri.getRawQuery());
        }
        request.append(" HTTP/1.1\r\n");

        request.append("Host: ").append(uri.getHost());
        if (uri.getPort() > 0)
        {
            request.append(':').append(uri.getPort());
        }
        request.append("\r\n");
        request.append("Upgrade: websocket\r\n");
        request.append("Connection: Upgrade\r\n");
        request.append("Sec-WebSocket-Key: ").append(key).append("\r\n");

        if (StringUtil.isNotBlank(future.getOrigin()))
        {
            request.append("Origin: ").append(future.getOrigin()).append("\r\n");
        }

        request.append("Sec-WebSocket-Version: 13\r\n"); // RFC-6455 specified version

        Map<String, String> cookies = future.getCookies();
        if ((cookies != null) && (cookies.size() > 0))
        {
            for (String cookie : cookies.keySet())
            {
                request.append("Cookie: ");
                request.append(QuotedStringTokenizer.quoteIfNeeded(cookie,COOKIE_DELIM));
                request.append("=");
                request.append(QuotedStringTokenizer.quoteIfNeeded(cookies.get(cookie),COOKIE_DELIM));
                request.append("\r\n");
            }
        }

        request.append("\r\n");

        // TODO: extensions
        // TODO: write to connection
        byte rawreq[] = StringUtil.getUtf8Bytes(request.toString());
        ByteBuffer buf = bufferPool.acquire(rawreq.length,false);
        try
        {
            FutureCallback<ConnectFuture> callback = new FutureCallback<>();
            getEndPoint().write(future,callback,buf);
            // TODO: block on read?
            // TODO: read response & upgrade via async callback
            callback.get();
        }
        finally
        {
            bufferPool.release(buf);
        }
    }

    @Override
    public void onFillable()
    {
        // TODO Auto-generated method stub

    }
}
