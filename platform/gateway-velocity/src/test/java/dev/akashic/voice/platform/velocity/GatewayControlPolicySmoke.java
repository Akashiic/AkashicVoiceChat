package dev.akashic.voice.platform.velocity;

import static dev.akashic.voice.platform.velocity.GatewayControlPolicy.Action.*;
import static dev.akashic.voice.platform.velocity.GatewayControlPolicy.Direction.*;

/** Executed decision logic; NOT a replacement for real Velocity API/event integration tests. */
final class GatewayControlPolicySmoke {
    private static int passed;
    @FunctionalInterface interface Test { void run(); }
    private static void test(String name, Test test) { test.run(); passed++; System.out.println("PASS "+name); }
    private static void expect(GatewayControlPolicy.Action expected, boolean current,
                               GatewayControlPolicy.Direction direction, int tag, int length,
                               boolean healthy, boolean bound) {
        var actual=GatewayControlPolicy.classify(true,false,current,direction,tag,length,healthy,bound);
        if(actual!=expected)throw new AssertionError("expected="+expected+" actual="+actual+" tag="+tag);
    }
    public static void main(String[] args) {
        test("unrelated_channel_ignored",()->{if(GatewayControlPolicy.classify(false,false,true,CLIENT,0,1,true,false)!=IGNORE)throw new AssertionError();});
        test("already_handled_ignored",()->{if(GatewayControlPolicy.classify(true,true,true,CLIENT,0,1,true,false)!=IGNORE)throw new AssertionError();});
        test("stale_connections_drop_every_tag",()->{for(int tag=0;tag<256;tag++)for(var dir:GatewayControlPolicy.Direction.values())expect(DROP,false,dir,tag,1,false,false);});
        test("unknown_source_dropped",()->{for(int tag=0;tag<256;tag++)expect(DROP,true,OTHER,tag,1,false,false);});
        test("client_cannot_send_server_controls",()->{for(int tag:new int[]{1,6,7,8,9})for(boolean ready:new boolean[]{false,true})for(boolean bound:new boolean[]{false,true})expect(DROP,true,CLIENT,tag,20,ready,bound);});
        test("backend_cannot_send_client_commands",()->{for(int tag:new int[]{0,2,3,4,5})expect(DROP,true,BACKEND,tag,20,false,false);});
        test("native_bootstrap_preserved",()->expect(NATIVE,true,CLIENT,0,1,false,false));
        test("managed_bootstrap_consumed",()->expect(BOOTSTRAP,true,CLIENT,0,1,true,false));
        test("bootstrap_length_is_exact",()->{for(int length:new int[]{0,2,100,30000})expect(DROP,true,CLIENT,0,length,true,false);});
        test("managed_group_requires_binding",()->{for(int tag=2;tag<=5;tag++)expect(DROP,true,CLIENT,tag,20,true,false);});
        test("native_group_keeps_local_authority",()->{for(int tag=2;tag<=5;tag++)expect(NATIVE,true,CLIENT,tag,20,false,false);});
        test("group_authority_survives_agent_outage",()->{for(int tag=2;tag<=5;tag++)expect(GROUP,true,CLIENT,tag,20,false,true);for(int tag:new int[]{1,6,7,8,9})expect(DROP,true,BACKEND,tag,20,false,true);});
        test("native_request_budget",()->{expect(NATIVE,true,CLIENT,2,259,false,false);expect(DROP,true,CLIENT,2,260,false,false);});
        test("native_server_controls_preserved",()->{for(int tag:new int[]{1,6,7,8,9})expect(NATIVE,true,BACKEND,tag,20,false,false);});
        test("healthy_backend_cannot_race_binding",()->{for(int tag:new int[]{1,6,7,8,9})expect(DROP,true,BACKEND,tag,20,true,false);});
        test("unknown_tags_and_empty_messages_drop",()->{for(var dir:GatewayControlPolicy.Direction.values()){for(int tag=10;tag<256;tag++)expect(DROP,true,dir,tag,20,false,false);for(int tag=0;tag<256;tag++)expect(DROP,true,dir,tag,0,false,false);}});
        test("forwarding_direction_invariant",()->{
            for(var dir:GatewayControlPolicy.Direction.values())for(boolean ready:new boolean[]{false,true})for(boolean bound:new boolean[]{false,true})for(int tag=0;tag<256;tag++) {
                var action=GatewayControlPolicy.classify(true,false,true,dir,tag,1,ready,bound);
                if(action==BOOTSTRAP&&(dir!=CLIENT||tag!=0||!ready))throw new AssertionError();
                if(action==GROUP&&(dir!=CLIENT||tag<2||tag>5||!bound))throw new AssertionError();
                if(action==NATIVE&&ready)throw new AssertionError("healthy managed traffic leaked");
            }
        });
        System.out.println("SUMMARY gateway_control_policy passed="+passed+" failed=0");
    }
}
