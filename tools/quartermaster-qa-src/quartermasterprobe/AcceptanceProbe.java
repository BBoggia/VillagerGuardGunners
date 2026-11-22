package quartermasterprobe;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraftforge.event.TickEvent;

/** Optional acceptance workloads; absent from production and inactive without an explicit mode. */
final class AcceptanceProbe {
    private int tick;
    private boolean done;
    static final List<String> CASES=new ArrayList<>();
    static Path out(){return Path.of(System.getProperty("vgg.acceptance.output"));}
    static void check(String name,boolean okay){System.out.println("VGG acceptance "+name+"="+okay);if(!okay)throw new AssertionError(name);CASES.add(name);}
    void tick(TickEvent.ServerTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END||System.getProperty("vgg.acceptance.mode")==null)return;
        try {
            tick++;boolean complete=switch(System.getProperty("vgg.acceptance.mode")) {
                case "legacy" -> LegacyWorld.seed(event.getServer().overworld(),tick);
                case "upgrade", "upgrade-restart" -> UpgradeCases.tick(event.getServer().overworld(),tick);
                case "performance" -> PerformanceCases.tick(event.getServer().overworld(),tick);
                case "multiplayer" -> MultiplayerCases.tick(event.getServer().overworld(),tick);
                default -> throw new IllegalArgumentException("Unknown acceptance mode");
            };
            if(complete)finish(event,null);
        }catch(Throwable e){e.printStackTrace();finish(event,e.toString());}
    }
    private void finish(TickEvent.ServerTickEvent event,String failure) {
        done=true;
        try{Files.writeString(out(),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",failure==null,"failure",failure==null?"":failure,"cases",CASES))+"\n");}
        catch(Exception e){throw new RuntimeException(e);}
        event.getServer().halt(false);
    }
}
