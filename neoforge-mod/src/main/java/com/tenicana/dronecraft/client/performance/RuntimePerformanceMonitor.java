package com.tenicana.dronecraft.client.performance;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.lwjgl.opengl.GL11;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.DroneClientState;
import com.tenicana.dronecraft.entity.DroneEntity;

import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.fml.ModList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Opt-in actual-play capture. Hot paths append primitive values; IO is off-thread. */
public final class RuntimePerformanceMonitor {
    private static final int FRAME_CAPACITY=65_536, EVENT_CAPACITY=65_536;
    private static final ThreadLocal<Integer> FRAME_DEPTH=ThreadLocal.withInitial(() -> 0);
    private static final AtomicLong CAPTURE_IDS=new AtomicLong();
    private static final AtomicBoolean EXPORTING=new AtomicBoolean();
    private static final ExecutorService WRITER=Executors.newSingleThreadExecutor(task -> {
        Thread thread=new Thread(task,"FPV performance report writer"); thread.setDaemon(true); return thread;
    });
    private static final List<GarbageCollectorMXBean> COLLECTORS=ManagementFactory.getGarbageCollectorMXBeans();
    private static volatile Session session;
    private static volatile CompletableFuture<Path> lastExport;
    private static volatile Path lastReport;
    private static volatile String lastError;
    private static boolean hudVisible=true;
    private static boolean stopping;
    private static double targetFps=120;
    private static long hudUpdated;
    private static PerformanceCapture.Live live;
    private static String[] hudLines=new String[0];
    private static String hudFooter="";

    private RuntimePerformanceMonitor() { }

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
            Commands.literal("fpvperf")
                .executes(context -> { help(Minecraft.getInstance()); return 1; })
                .then(Commands.literal("start")
                    .executes(context -> startCommand(Minecraft.getInstance(),120,false))
                    .then(Commands.argument("seconds",IntegerArgumentType.integer(5,600))
                        .executes(context -> startCommand(Minecraft.getInstance(),IntegerArgumentType.getInteger(context,"seconds"),false))))
                .then(Commands.literal("startgpu")
                    .executes(context -> startCommand(Minecraft.getInstance(),120,true))
                    .then(Commands.argument("seconds",IntegerArgumentType.integer(5,600))
                        .executes(context -> startCommand(Minecraft.getInstance(),IntegerArgumentType.getInteger(context,"seconds"),true))))
                .then(Commands.literal("stop").executes(context -> {
                    Minecraft client=Minecraft.getInstance();
                    if (session==null) feedback(client,"当前没有采集；用 /fpvperf start 开始。"); else stop(client);
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> { status(Minecraft.getInstance()); return 1; }))
                .then(Commands.literal("mark")
                    .then(Commands.argument("label",StringArgumentType.greedyString()).executes(context -> {
                        Session current=session;
                        boolean ok=current!=null && current.capture.mark(System.nanoTime(),StringArgumentType.getString(context,"label"));
                        feedback(Minecraft.getInstance(),ok?"已添加时间标记。":"未在采集，或本次已达到 128 个标记上限。"); return 1;
                    })))
                .then(Commands.literal("hud")
                    .then(Commands.literal("on").executes(context -> { hudVisible=true; return 1; }))
                    .then(Commands.literal("off").executes(context -> { hudVisible=false; return 1; })))
                .then(Commands.literal("target")
                    .then(Commands.argument("fps",IntegerArgumentType.integer(30,1000)).executes(context -> {
                        if(session!=null) feedback(Minecraft.getInstance(),"停止当前采集后再设置参考 FPS；它只改变报告预算，不改变游戏帧率。");
                        else { targetFps=IntegerArgumentType.getInteger(context,"fps"); feedback(Minecraft.getInstance(),"参考预算已设为 "+(int)targetFps+" FPS；游戏帧率设置不变。"); }
                        return 1;
                    })))
        ));
    }
    public static void registerGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR,ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID,"performance_monitor"),RuntimePerformanceMonitor::renderHud);
    }
    public static void onClientStopping(Minecraft client) {
            stopping=true;
            if(session!=null) stop(client);
            CompletableFuture<Path> future=lastExport;
            if(future!=null && !future.isDone()) {
                try { future.get(5,TimeUnit.SECONDS); }
                catch(Exception error) { FpvDronecraftMod.LOGGER.warn("Performance export did not finish within shutdown wait; a partial report may remain",error); }
            }
    }

    public static boolean enabled() { return session!=null; }
    public static long captureId() { Session current=session; return current==null?0:current.id; }
    public static boolean gpuTimingEnabled() { Session current=session; return current!=null && current.gpu; }
    public static long currentFrameToken() {
        Session current=session;
        return current==null || FRAME_DEPTH.get()!=1 ? 0 : current.frameToken;
    }
    public static long beginFrame(Minecraft client,boolean render) {
        int depth=FRAME_DEPTH.get()+1; FRAME_DEPTH.set(depth);
        Session current=session;
        if(current==null || !render || depth!=1) return 0;
        long now=System.nanoTime();
        if(now-current.lastMemorySample>=1_000_000_000L) {
            long gc=gcTime();
            current.gcDelta=Math.max(0,gc-current.lastGcTime); current.lastGcTime=gc;
            current.heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
            current.lastMemorySample=now;
            if(current.gcDelta>0) current.capture.gc(now,current.gcDelta,current.heap);
        } else current.gcDelta=0;
        DroneEntity drone=DroneClientState.controlledDrone();
        current.controlledId=drone==null?-1:drone.getId();
        int flags=(client.level!=null?PerformanceCapture.LEVEL:0)
                |(client.screen!=null?PerformanceCapture.SCREEN:0)
                |(client.isPaused()?PerformanceCapture.PAUSED:0)
                |(client.isWindowActive()?PerformanceCapture.FOCUSED:0)
                |(DroneClientState.isFpvActive(client.level)?PerformanceCapture.FPV:0);
        long token=(current.id<<32)|(++current.frameSequence&0xffffffffL);
        current.frameToken=token;
        current.capture.beginFrame(token,now,flags,current.controlledId,current.heap,current.gcDelta);
        return token;
    }
    public static void endFrame(long token,long elapsed,boolean completed) {
        try {
            Session current=forFrame(token);
            if(current!=null) {
                current.capture.finishFrame(token,elapsed,completed); current.frameToken=0;
                if(System.nanoTime()>=current.deadline) stop(Minecraft.getInstance());
            }
        } finally { FRAME_DEPTH.set(Math.max(0,FRAME_DEPTH.get()-1)); }
    }
    public static long beginRender(boolean render) { return render?currentFrameToken():0; }
    public static void endRender(long token,long elapsed,boolean completed) {
        Session current=forFrame(token); if(current!=null) current.capture.render(token,elapsed,completed);
    }
    public static void recordClientTick(long elapsed) {
        long token=currentFrameToken(); Session current=forFrame(token); if(current!=null) current.capture.clientTick(token,elapsed);
    }
    public static void recordSwap(long elapsed) {
        long token=currentFrameToken(); Session current=forFrame(token); if(current!=null) current.capture.swap(token,elapsed);
    }
    public static void recordFrameLimit(long elapsed) {
        long token=currentFrameToken(); Session current=forFrame(token); if(current!=null) current.capture.limit(token,elapsed);
    }
    public static void acceptGpuTiming(long token,long elapsed) { Session current=forFrame(token); if(current!=null) current.capture.gpu(token,elapsed); }
    public static void gpuSampleSkipped() { Session current=session; if(current!=null) current.capture.gpuSkipped(); }
    public static void setGpuStatus(String value) { Session current=session; if(current!=null) current.gpuStatus=value; }

    public static void recordServerTick(long id,long start,long end,boolean paused) {
        Session current=forCapture(id); if(current==null) return;
        current.capture.serverTick(start,end,paused);
        current.serverTickMs=(end-start)/1e6; current.serverPaused=paused;
    }
    public static void saveStarted(long id,long start,boolean silent,boolean flush,boolean force) {
        Session current=forCapture(id); if(current!=null) current.capture.saveStarted(start,silent,flush,force);
    }
    public static void saveFinished(long id,long start,long end,boolean result) { finishSave(id,start,end,result?1:0); }
    public static void saveFailed(long id,long start,long end) { finishSave(id,start,end,-1); }
    private static void finishSave(long id,long start,long end,int result) {
        Session current=forCapture(id); if(current!=null) { current.capture.saveFinished(start,end,result); current.lastSaveMs=(end-start)/1e6; }
    }
    public static void recordDroneTick(long id,boolean clientSide,int entityId,long start,long end,long interval) {
        Session current=forCapture(id); if(current!=null) current.capture.droneTick(clientSide,entityId,start,end,interval);
    }
    public static void recordPoseArrival(long id,int entityId,boolean movement,long now,long interval) {
        Session current=forCapture(id); if(current==null) return;
        current.capture.pose(entityId,movement,now,interval);
        if(entityId==current.controlledId && interval>=0) {
            if(movement) current.movementIntervalMs=interval/1e6; else current.metadataIntervalMs=interval/1e6;
        }
    }
    private static Session forCapture(long id) { Session current=session; return current!=null && id!=0 && current.id==id?current:null; }
    private static Session forFrame(long token) { return token==0?null:forCapture(token>>>32); }

    public static boolean start(Minecraft client,int seconds,boolean gpu) {
        if(!client.isSameThread()) throw new IllegalStateException("start on client thread");
        if(seconds<5 || seconds>600) throw new IllegalArgumentException("seconds 5..600");
        if(session!=null || EXPORTING.get()) return false;
        AsyncGpuTimer.close();
        long now=System.nanoTime();
        Session current=new Session(CAPTURE_IDS.incrementAndGet(),now,seconds,gpu,client.gameDirectory.toPath(),metadata(client),targetFps);
        session=current; live=null; hudUpdated=0; lastError=null;
        feedback(client,"性能采集已开始："+seconds+" 秒，"+(gpu?"含异步 GPU 计时":"帧时间 / 服务端 / 保存")+"；/fpvperf stop 提前停止并导出。");
        return true;
    }
    public static CompletableFuture<Path> stop(Minecraft client) {
        if(!client.isSameThread()) throw new IllegalStateException("stop on client thread");
        Session current=session;
        if(current==null) return lastExport;
        AsyncGpuTimer.service();
        session=null;
        current.capture.seal(System.nanoTime());
        AsyncGpuTimer.close();
        EXPORTING.set(true); live=null;
        if(!stopping) feedback(client,"采集已停止，正在后台导出帧时间报告。");
        CompletableFuture<Path> future=CompletableFuture.supplyAsync(() -> {
            try { return PerformanceReportWriter.export(current.directory.resolve("performance-diagnostics"),current.label,
                    current.capture.snapshot(),current.metadata,current.targetFps,current.gpuStatus); }
            catch(Exception error) { throw new java.util.concurrent.CompletionException(error); }
        },WRITER);
        lastExport=future;
        future.whenComplete((path,error) -> {
            EXPORTING.set(false);
            if(error==null) { lastReport=path; FpvDronecraftMod.LOGGER.info("Performance report exported: {}",path); }
            else { lastError=error.getCause()==null?error.toString():error.getCause().toString(); FpvDronecraftMod.LOGGER.error("Performance report export failed",error); }
            if(!stopping) client.execute(() -> feedback(client,error==null?"性能报告已保存："+path:"性能报告导出失败："+lastError));
        });
        return future;
    }
    public static CompletableFuture<Path> lastExport() { return lastExport; }
    public static Path lastReport() { return lastReport; }
    private static int startCommand(Minecraft client,int seconds,boolean gpu) {
        if(!start(client,seconds,gpu)) feedback(client,EXPORTING.get()?"上一份报告仍在导出，完成后再开始。":"已经在采集；先 /fpvperf stop。");
        return 1;
    }
    private static void status(Minecraft client) {
        Session current=session;
        if(current!=null) feedback(client,"正在采集，剩余约 "+Math.max(0,(current.deadline-System.nanoTime())/1_000_000_000L)+" 秒；GPU："+current.gpuStatus);
        else if(EXPORTING.get()) feedback(client,"报告正在后台导出。");
        else if(lastError!=null) feedback(client,"上次导出失败："+lastError);
        else feedback(client,lastReport==null?"监测未开启。/fpvperf start 120":"监测未开启；上次报告："+lastReport);
    }
    private static void help(Minecraft client) {
        feedback(client,"/fpvperf start [5..600秒] | startgpu [秒] | stop | mark 说明 | hud on/off | target FPS | status");
    }
    private static void feedback(Minecraft client,String text) { client.gui.getChat().addMessage(Component.literal("[FPV 性能] "+text)); }
    private static long gcTime() {
        long total=0; for(GarbageCollectorMXBean bean:COLLECTORS) total+=Math.max(0,bean.getCollectionTime()); return total;
    }
    private static Map<String,Object> metadata(Minecraft client) {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("mod_version",ModList.get().getModContainerById(FpvDronecraftMod.MOD_ID).orElseThrow().getModInfo().getVersion().toString());
        values.put("minecraft_version",SharedConstants.getCurrentVersion().getName());
        values.put("java_version",System.getProperty("java.version"));
        values.put("gl_vendor",GL11.glGetString(GL11.GL_VENDOR)); values.put("gl_renderer",GL11.glGetString(GL11.GL_RENDERER));
        values.put("gl_version",GL11.glGetString(GL11.GL_VERSION));
        values.put("vsync",client.options.enableVsync().get()); values.put("render_distance",client.options.renderDistance().get());
        values.put("simulation_distance",client.options.simulationDistance().get()); values.put("framerate_option",client.options.framerateLimit().get());
        values.put("framebuffer_width",client.getWindow().getWidth()); values.put("framebuffer_height",client.getWindow().getHeight());
        values.put("fabric_client_gametest_active",System.getProperties().containsKey("fabric.client.gametest"));
        values.put("started_at",ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
        values.put("frame_capacity",FRAME_CAPACITY); values.put("event_capacity",EVENT_CAPACITY);
        return values;
    }

    private static void renderHud(GuiGraphics graphics,net.minecraft.client.DeltaTracker delta) {
        Session current=session; Minecraft client=Minecraft.getInstance();
        if(current==null || !hudVisible || client.player==null || client.options.hideGui) return;
        long now=System.nanoTime();
        if(live==null || now-hudUpdated>250_000_000L) {
            live=current.capture.live(240,1000/current.targetFps); hudUpdated=now;
            PerformanceCapture.Statistics stats=live.intervals();
            hudLines=new String[]{
                "FPV 性能  "+Math.max(0,(current.deadline-now)/1_000_000_000L)+"s  "+(live.activeSaves()>0?"保存中":"采集中"),
                String.format(Locale.ROOT,"帧间隔 %.2f ms  ≈%.0f FPS",stats.meanMs(),stats.meanMs()>0?1000/stats.meanMs():0),
                String.format(Locale.ROOT,"P95 %.2f  P99 %.2f  峰值 %.2f ms",stats.p95Ms(),stats.p99Ms(),stats.maxMs()),
                String.format(Locale.ROOT,"渲染提交 %.2f | swap %.2f | 限帧 %.2f",live.renderMs(),live.swapMs(),live.limiterMs()),
                live.gpuMs()<0?(current.gpu?current.gpuStatus.startsWith("unavailable:")?"GPU：不可用，原因见报告":"GPU：等待异步查询":"GPU：未开启 (/fpvperf startgpu)"):String.format(Locale.ROOT,"GPU 绘制范围 %.2f ms",live.gpuMs()),
                String.format(Locale.ROOT,"服务端 %.2f ms%s | 最近保存 %.1f ms",current.serverTickMs,current.serverPaused?" (暂停)":"",current.lastSaveMs),
                "位置接收 "+intervalText(current.movementIntervalMs)+" | 数据接收 "+intervalText(current.metadataIntervalMs)+" ms"
            };
            hudFooter=String.format(Locale.ROOT,"黄线 %.2f ms / %d FPS；停止后导出",1000/current.targetFps,(int)current.targetFps);
        }
        int width=Math.min(294,client.getWindow().getGuiScaledWidth()-16),x=client.getWindow().getGuiScaledWidth()-width-8,y=48;
        graphics.fill(x,y,x+width,y+126,0xCB10151B);
        for(int n=0;n<hudLines.length;n++) graphics.drawString(client.font,hudLines[n],x+6,y+5+n*10,0xFFE3EFF5,false);
        int graphTop=y+78,graphBottom=y+117,graphWidth=width-12;
        double budget=1000/current.targetFps,scale=Math.max(budget*3,Math.min(100,live.intervals().maxMs()));
        int budgetY=graphBottom-(int)Math.round((graphBottom-graphTop)*budget/scale);
        graphics.fill(x+6,budgetY,x+width-6,budgetY+1,0xAAE6BB58);
        double[] chart=live.chart();
        int bars=Math.min(120,graphWidth);
        for(int px=0;px<bars;px++) {
            int a=px*chart.length/bars,b=Math.min(chart.length,Math.max(a+1,(px+1)*chart.length/bars));
            double peak=0; for(int index=a;index<b;index++)peak=Math.max(peak,chart[index]);
            int height=(int)Math.min(graphBottom-graphTop,Math.round((graphBottom-graphTop)*peak/scale));
            if(height>0) graphics.fill(x+6+px*graphWidth/bars,graphBottom-height,x+6+(px+1)*graphWidth/bars,graphBottom,peak>budget*2?0xFFE96B72:peak>budget?0xFFFFC45B:0xFF56D6AA);
        }
        graphics.drawString(client.font,hudFooter,x+6,y+117,0xFFABBFC9,false);
    }
    private static String intervalText(double value) { return value<0?"--":String.format(Locale.ROOT,"%.1f",value); }
    private static final class Session {
        final long id,deadline;
        final boolean gpu;
        final PerformanceCapture capture;
        final Path directory;
        final Map<String,Object> metadata;
        final double targetFps;
        final String label;
        long frameSequence,frameToken,lastMemorySample,lastGcTime,heap,gcDelta;
        volatile int controlledId=-1;
        volatile String gpuStatus;
        volatile double serverTickMs,lastSaveMs,movementIntervalMs=-1,metadataIntervalMs=-1;
        volatile boolean serverPaused;
        Session(long id,long now,int seconds,boolean gpu,Path directory,Map<String,Object> metadata,double targetFps) {
            this.id=id; deadline=now+seconds*1_000_000_000L; this.gpu=gpu;
            this.directory=directory; this.metadata=metadata; this.targetFps=targetFps;
            capture=new PerformanceCapture(now,FRAME_CAPACITY,EVENT_CAPACITY);
            label=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")));
            gpuStatus=gpu?"waiting for GPU queries":"off (startgpu 可开启)";
            lastGcTime=gcTime(); lastMemorySample=now-1_000_000_000L;
        }
    }
}
