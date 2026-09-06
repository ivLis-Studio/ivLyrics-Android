package kr.ivlis.ivlyricsandroid;

import org.junit.Test;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class LatestLyricsRequestTest {
    @Test public void latestTrackDisconnectsBlockedProviderWithoutWaitingForReadTimeout() throws Exception {
        ThreadPoolExecutor executor=(ThreadPoolExecutor)Executors.newFixedThreadPool(1);
        LatestLyricsRequest queue=new LatestLyricsRequest(executor);
        CountDownLatch reading=new CountDownLatch(1), disconnected=new CountDownLatch(1), complete=new CountDownLatch(1);
        AtomicBoolean stalePublished=new AtomicBoolean(), fallbackOpened=new AtomicBoolean();
        HttpURLConnection connection=new HttpURLConnection(new URL("http://fixture.invalid")) {
            public void connect() {} public boolean usingProxy(){return false;}
            public void disconnect(){disconnected.countDown();}
        };
        try {
            RequestCancellation first=queue.begin();
            queue.submit(first,()->{
                try(RequestCancellation.AutoCloseableConnection registration=RequestCancellation.register(connection)) {
                    reading.countDown();
                    while(disconnected.getCount()!=0) try { disconnected.await(); } catch(InterruptedException ignored) {}
                    // Provider fallback must stop even if its transport swallowed interruption.
                    RequestCancellation.check(); fallbackOpened.set(true);
                }
                if(!first.isCancelled()) stalePublished.set(true);
            });
            assertTrue(reading.await(3,TimeUnit.SECONDS));
            RequestCancellation latest=queue.begin();
            queue.submit(latest,complete::countDown);
            assertTrue(complete.await(3,TimeUnit.SECONDS));
            assertFalse(stalePublished.get()); assertFalse(fallbackOpened.get());
        } finally {queue.cancel();executor.shutdownNow();}
    }
    @Test public void skipsQueuedObsoleteTracksAndKeepsUnrelatedManualWork() throws Exception {
        ThreadPoolExecutor executor=(ThreadPoolExecutor)Executors.newFixedThreadPool(1);
        LatestLyricsRequest queue=new LatestLyricsRequest(executor);
        CountDownLatch blocker=new CountDownLatch(1), entered=new CountDownLatch(1), finished=new CountDownLatch(1);
        List<String> results=new ArrayList<>();
        try {
            executor.execute(()->{entered.countDown();try{blocker.await();}catch(InterruptedException ignored){}});
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            RequestCancellation a=queue.begin();queue.submit(a,()->results.add("A"));
            RequestCancellation b=queue.begin();queue.submit(b,()->results.add("B"));
            executor.execute(()->{assertNull(RequestCancellation.current());results.add("manual");});
            RequestCancellation c=queue.begin();queue.submit(c,()->{results.add("C");finished.countDown();});
            assertEquals(2,executor.getQueue().size());
            blocker.countDown(); assertTrue(finished.await(3,TimeUnit.SECONDS));
            assertEquals(List.of("manual","C"),results);
        } finally {blocker.countDown();queue.cancel();executor.shutdownNow();}
    }
    @Test public void sameTrackMetadataRestartCancelsPreviousScopeAndRestoresThreadLocal() {
        RequestCancellation request=new RequestCancellation();
        request.run(()->{assertSame(request,RequestCancellation.current());request.cancel();});
        assertNull(RequestCancellation.current());
        AtomicBoolean ran=new AtomicBoolean();
        request.run(()->ran.set(true));assertFalse(ran.get());
    }
    @Test public void queuedCallbacksKeepTheirOriginalScopeAcrossSameTrackRestart() {
        List<Runnable> mainQueue = new ArrayList<>();
        List<String> delivered = new ArrayList<>();
        RequestCancellation first = new RequestCancellation();
        first.run(() -> {
            for (String event : List.of("artwork", "metadata", "log")) {
                mainQueue.add(RequestCancellation.guard(() -> delivered.add("old-" + event)));
            }
        });
        first.cancel();
        RequestCancellation enriched = new RequestCancellation();
        enriched.run(() -> mainQueue.add(RequestCancellation.guard(() -> delivered.add("enriched"))));
        mainQueue.add(RequestCancellation.guard(() -> delivered.add("manual")));
        mainQueue.forEach(Runnable::run);
        assertEquals(List.of("enriched", "manual"), delivered);
        mainQueue.clear();
        mainQueue.add(RequestCancellation.guard(enriched, () -> delivered.add("closed")));
        enriched.cancel();
        mainQueue.forEach(Runnable::run);
        assertEquals(List.of("enriched", "manual"), delivered);
    }

}
