package com.tenicana.dronecraft.client.performance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded primitive capture storage. Contains no Minecraft/world objects or IO. */
public final class PerformanceCapture {
    public static final int FRAME_COLUMNS = 18;
    public static final int TOKEN=0, START=1, INTERVAL=2, TOTAL=3, RENDER=4, SWAP=5, LIMIT=6, GPU=7,
            CLIENT_TICKS=8, CLIENT_TICK_SUM=9, CLIENT_TICK_MAX=10, FLAGS=11, ENTITY=12,
            HEAP=13, GC_DELTA=14, SERVER_AGE=15, POSE_AGE=16, SAVE_OVERLAP=17;
    public static final int LEVEL=1, SCREEN=2, PAUSED=4, FOCUSED=8, FPV=16;
    public static final int SERVER_TICK=1, PAUSED_SERVER_TICK=2, SAVE=3, CLIENT_DRONE=4,
            SERVER_DRONE=5, METADATA=6, MOVEMENT=7, GC_OBSERVATION=8, MARKER=9, SAVE_BEGIN=10;
    public static final int EVENT_COLUMNS=8;
    private final long[][] frames;
    private final long[][] events;
    private final List<String> markers = new ArrayList<>();
    private final long origin;
    private int frameSize, frameCursor, eventSize, eventCursor;
    private long frameCount, eventCount, incompleteFrames, gpuSkipped;
    private long openToken, openStart, lastStart, render, swap, limit, tickCount, tickSum, tickMax;
    private long heap, gcDelta, lastServerTick, lastPose, openSaveRevision, saveRevision;
    private int flags, entityId=-1, openSaves;
    private boolean sealed;
    private long finished;

    public PerformanceCapture(long origin, int frameCapacity, int eventCapacity) {
        if (frameCapacity < 2 || eventCapacity < 2) throw new IllegalArgumentException("capacity must be >= 2");
        this.origin=origin;
        frames=new long[FRAME_COLUMNS][frameCapacity];
        events=new long[EVENT_COLUMNS][eventCapacity];
    }

    public synchronized void beginFrame(long token, long now, int flags, int controlledEntityId, long heap, long gcDelta) {
        if (sealed || token == 0) return;
        if (openToken != 0) incompleteFrames++;
        if (controlledEntityId != entityId) lastPose=0;
        this.entityId=controlledEntityId;
        openToken=token; openStart=now; this.flags=flags; this.heap=heap; this.gcDelta=gcDelta;
        openSaveRevision=saveRevision;
        render=swap=limit=tickCount=tickSum=tickMax=0;
    }

    public synchronized void render(long token, long elapsed, boolean completed) {
        if (!sealed && completed && token == openToken && token != 0) render+=Math.max(0, elapsed);
    }
    public synchronized void swap(long token, long elapsed) {
        if (!sealed && token == openToken && token != 0) swap+=Math.max(0, elapsed);
    }
    public synchronized void limit(long token, long elapsed) {
        if (!sealed && token == openToken && token != 0) limit+=Math.max(0, elapsed);
    }
    public synchronized void clientTick(long token, long elapsed) {
        if (!sealed && token == openToken && token != 0) {
            elapsed=Math.max(0,elapsed); tickCount++; tickSum+=elapsed; tickMax=Math.max(tickMax,elapsed);
        }
    }
    public synchronized void finishFrame(long token, long elapsed, boolean completed) {
        if (sealed || token == 0 || openToken != token) return;
        if (!completed) { incompleteFrames++; openToken=0; lastStart=openStart; return; }
        int row=frameCursor;
        frames[TOKEN][row]=token; frames[START][row]=openStart;
        frames[INTERVAL][row]=lastStart == 0 ? -1 : Math.max(0,openStart-lastStart);
        frames[TOTAL][row]=Math.max(0,elapsed); frames[RENDER][row]=render;
        frames[SWAP][row]=swap; frames[LIMIT][row]=limit; frames[GPU][row]=-1;
        frames[CLIENT_TICKS][row]=tickCount; frames[CLIENT_TICK_SUM][row]=tickSum;
        frames[CLIENT_TICK_MAX][row]=tickMax; frames[FLAGS][row]=flags;
        frames[ENTITY][row]=entityId; frames[HEAP][row]=heap; frames[GC_DELTA][row]=gcDelta;
        long frameEnd=openStart+Math.max(0,elapsed);
        frames[SERVER_AGE][row]=lastServerTick == 0 ? -1 : Math.max(0,frameEnd-lastServerTick);
        frames[POSE_AGE][row]=lastPose == 0 ? -1 : Math.max(0,frameEnd-lastPose);
        frames[SAVE_OVERLAP][row]=(openSaves > 0 || saveRevision != openSaveRevision) ? 1 : 0;
        lastStart=openStart; openToken=0;
        frameCount++; frameSize=Math.min(frameSize+1,frames[0].length);
        frameCursor=(frameCursor+1)%frames[0].length;
    }

    public synchronized void gpu(long token, long elapsed) {
        if (sealed || token == 0 || elapsed < 0) return;
        // Frame tokens increase monotonically, even if an incomplete frame leaves
        // a gap. Search the chronological ring in O(log capacity), including a
        // genuinely delayed GPU result rather than silently imposing a 32-frame cutoff.
        int low=0,high=frameSize-1;
        while(low<=high) {
            int mid=(low+high)>>>1,row=Math.floorMod(frameCursor-frameSize+mid,frames[0].length);
            long found=frames[TOKEN][row];
            if(found==token) { frames[GPU][row]=elapsed; return; }
            if(found<token) low=mid+1; else high=mid-1;
        }
        gpuSkipped++;
    }
    public synchronized void gpuSkipped() { if (!sealed) gpuSkipped++; }

    public synchronized void serverTick(long start, long end, boolean paused) {
        if (sealed) return;
        long interval=lastServerTick == 0 ? -1 : Math.max(0,start-lastServerTick);
        lastServerTick=start;
        event(paused ? PAUSED_SERVER_TICK : SERVER_TICK,start,Math.max(0,end-start),interval,0,0,0,0);
    }
    public synchronized void saveStarted(long start, boolean silent, boolean flush, boolean force) {
        if (sealed) return;
        openSaves++; saveRevision++;
        event(SAVE_BEGIN,start,0,saveFlags(silent,flush,force),0,0,0,0);
    }
    public synchronized void saveFinished(long start, long end, int result) {
        if (sealed) return;
        openSaves=Math.max(0,openSaves-1); saveRevision++;
        event(SAVE,start,Math.max(0,end-start),result,0,0,0,0);
    }
    public synchronized void droneTick(boolean client, int id, long start, long end, long interval) {
        if (!sealed) event(client ? CLIENT_DRONE : SERVER_DRONE,start,Math.max(0,end-start),interval,id,0,0,0);
    }
    public synchronized void pose(int id, boolean movement, long now, long interval) {
        if (sealed) return;
        if (id == entityId) lastPose=now;
        event(movement ? MOVEMENT : METADATA,now,0,interval,id,0,0,0);
    }
    public synchronized void gc(long now, long deltaMillis, long heap) {
        if (!sealed) event(GC_OBSERVATION,now,0,deltaMillis,heap,0,0,0);
    }
    public synchronized boolean mark(long now, String label) {
        if (sealed || markers.size() >= 128) return false;
        label=label.replace('\r',' ').replace('\n',' ');
        if (label.length()>80) label=label.substring(0,80);
        markers.add(label); event(MARKER,now,0,markers.size()-1,0,0,0,0); return true;
    }
    private void event(int kind,long start,long duration,long a,long b,long c,long d,long e) {
        int row=eventCursor;
        events[0][row]=kind; events[1][row]=start; events[2][row]=duration;
        events[3][row]=a; events[4][row]=b; events[5][row]=c; events[6][row]=d; events[7][row]=e;
        eventCount++; eventSize=Math.min(eventSize+1,events[0].length);
        eventCursor=(eventCursor+1)%events[0].length;
    }
    public synchronized void seal(long now) {
        if (!sealed) { sealed=true; finished=now; if (openToken != 0) incompleteFrames++; openToken=0; }
    }
    public synchronized Snapshot snapshot() {
        if (!sealed) throw new IllegalStateException("seal before export");
        return new Snapshot(origin,finished,copyRing(frames,frameCursor,frameSize),copyRing(events,eventCursor,eventSize),
                List.copyOf(markers),frameCount,eventCount,incompleteFrames,gpuSkipped,openSaves);
    }
    public synchronized Live live(int limitRows, double budgetMs) {
        int count=Math.min(frameSize,limitRows);
        double[] chart=new double[count];
        double renderSum=0,swapSum=0,limiterSum=0,gpuSum=0; int gpuCount=0;
        for (int n=0;n<count;n++) {
            int row=Math.floorMod(frameCursor-count+n,frames[0].length);
            chart[n]=frames[INTERVAL][row] < 0 ? 0 : frames[INTERVAL][row]/1e6;
            renderSum+=frames[RENDER][row]/1e6; swapSum+=frames[SWAP][row]/1e6; limiterSum+=frames[LIMIT][row]/1e6;
            if (frames[GPU][row]>=0) { gpuSum+=frames[GPU][row]/1e6; gpuCount++; }
        }
        Statistics stats=statistics(chart,budgetMs,true);
        return new Live(chart,stats,count==0?0:renderSum/count,count==0?0:swapSum/count,
                count==0?0:limiterSum/count,gpuCount==0?-1:gpuSum/gpuCount,openSaves,frameCount);
    }
    private static long[][] copyRing(long[][] source,int cursor,int size) {
        long[][] target=new long[source.length][size];
        for (int n=0;n<size;n++) {
            int row=Math.floorMod(cursor-size+n,source[0].length);
            for (int col=0;col<source.length;col++) target[col][n]=source[col][row];
        }
        return target;
    }
    public static Statistics statistics(double[] source,double budget,boolean excludeNonPositive) {
        double[] sorted=Arrays.stream(source).filter(v -> Double.isFinite(v) && v>=0 && (!excludeNonPositive || v>0)).sorted().toArray();
        if (sorted.length==0) return new Statistics(0,0,0,0,0,0,0,0,0);
        double mean=Arrays.stream(sorted).average().orElse(0); double variance=0; long over=0,overTwice=0;
        for (double value:sorted) { variance+=(value-mean)*(value-mean); if(value>budget)over++; if(value>budget*2)overTwice++; }
        return new Statistics(sorted.length,mean,percentile(sorted,.5),percentile(sorted,.95),percentile(sorted,.99),
                sorted[sorted.length-1],Math.sqrt(variance/sorted.length),over,overTwice);
    }
    private static double percentile(double[] sorted,double p) { return sorted[Math.max(0,(int)Math.ceil(p*sorted.length)-1)]; }
    private static int saveFlags(boolean silent,boolean flush,boolean force) { return (silent?1:0)|(flush?2:0)|(force?4:0); }
    public record Statistics(int samples,double meanMs,double p50Ms,double p95Ms,double p99Ms,double maxMs,
                             double standardDeviationMs,long overBudget,long overTwiceBudget) { }
    public record Live(double[] chart,Statistics intervals,double renderMs,double swapMs,double limiterMs,
                       double gpuMs,int activeSaves,long capturedFrames) { }
    public record Snapshot(long origin,long end,long[][] frames,long[][] events,List<String> markers,long totalFrames,
                           long totalEvents,long incompleteFrames,long gpuSkipped,int incompleteSaves) { }
}
