package dev.akashic.voice.voice.client.playback;

import java.util.*;
import dev.akashic.voice.client.gui.SpeakerPanelModel;

/** Real panel model and real per-speaker controls; no Minecraft or audio hardware is started. */
public final class SpeakerPanelSmoke {
    interface Scenario { void run(); }
    static int passed;
    static final UUID A = new UUID(1, 1), B = new UUID(2, 2), C = new UUID(3, 3);
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void test(String name, Scenario scenario) {
        scenario.run(); passed++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) {
        test("disconnected_panel_is_empty", SpeakerPanelSmoke::disconnected);
        test("roster_does_not_invent_heard_speakers", SpeakerPanelSmoke::observedOnly);
        test("defaults_are_100_percent_unmuted", SpeakerPanelSmoke::defaults);
        test("stable_sort_uses_uuid_tiebreaker", SpeakerPanelSmoke::sort);
        test("mute_preserves_percent", SpeakerPanelSmoke::mute);
        test("reset_only_changes_selected_speaker", SpeakerPanelSmoke::reset);
        test("zero_volume_is_distinct_from_mute", SpeakerPanelSmoke::zero);
        test("invalid_percent_rejected", SpeakerPanelSmoke::bounds);
        test("editing_requires_selection", SpeakerPanelSmoke::noSelection);
        test("invalid_row_rejected", SpeakerPanelSmoke::invalidRow);
        test("name_search_ignores_case", SpeakerPanelSmoke::nameSearch);
        test("uuid_search_preserves_identity", SpeakerPanelSmoke::uuidSearch);
        test("hidden_selection_cleared", SpeakerPanelSmoke::hidden);
        test("paging_is_bounded", SpeakerPanelSmoke::paging);
        test("page_change_clears_selection", SpeakerPanelSmoke::pageSelection);
        test("session_change_rejects_stale_edit", SpeakerPanelSmoke::sessionChange);
        test("reused_uuid_does_not_carry_old_preferences", SpeakerPanelSmoke::sameUuid);
        test("disconnect_clears_controls", SpeakerPanelSmoke::disconnect);
        test("rename_does_not_retarget_selection", SpeakerPanelSmoke::rename);
        test("duplicate_names_keep_distinct_ids", SpeakerPanelSmoke::duplicates);
        test("row_snapshots_are_immutable", SpeakerPanelSmoke::immutable);
        test("display_names_are_sanitized", SpeakerPanelSmoke::sanitize);
        test("external_mute_is_preserved_by_volume_edit", SpeakerPanelSmoke::externalMute);
        test("external_volume_is_preserved_by_mute", SpeakerPanelSmoke::externalVolume);
        test("caller_name_map_is_not_retained", SpeakerPanelSmoke::nameMap);
        System.out.println("SPEAKER_PANEL_OK scenarios=" + passed);
    }
    static final class Fixture {
        final SpeakerControls controls = new SpeakerControls();
        final SpeakerPanelModel panel = new SpeakerPanelModel();
        final Map<UUID, String> names = new HashMap<>();
        Fixture() { controls.observe(A); controls.observe(B); names.put(A,"Alice"); names.put(B,"Bob"); refresh(); }
        void refresh() { panel.refresh(controls, names, 5); }
        void first() { check(panel.select(0), "no first row"); check(panel.selected().id.equals(A), "wrong first row"); }
    }
    static void disconnected() {
        SpeakerPanelModel p=new SpeakerPanelModel();p.refresh(null,null,5);
        check(!p.connected() && p.rows().isEmpty() && p.pageCount()==1,"bad empty panel");
    }
    static void observedOnly() {
        Fixture f=new Fixture();f.names.put(C,"Carol");f.refresh();
        check(f.panel.total()==2 && !f.controls.snapshot().speakers().contains(C),"roster created a voice identity");
    }
    static void defaults() {
        Fixture f=new Fixture();f.first();check(f.panel.selected().percent==100 && !f.panel.selected().muted,"wrong defaults");
    }
    static void sort() {
        Fixture f=new Fixture();f.controls.observe(C);f.names.put(C,"alice");f.names.put(B,"ALICE");f.refresh();
        check(f.panel.rows().get(0).id.equals(A) && f.panel.rows().get(1).id.equals(B),"unstable name sort");
    }
    static void mute() {
        Fixture f=new Fixture();f.first();check(f.panel.setPercent(f.controls,35),"volume rejected");
        check(f.panel.toggleMute(f.controls) && f.controls.state(A).muted(),"mute failed");
        check(f.controls.state(A).percent()==35,"mute erased volume");
        check(f.panel.toggleMute(f.controls) && f.controls.state(A).percent()==35,"unmute erased volume");
    }
    static void reset() {
        Fixture f=new Fixture();f.controls.update(B,20,true);f.first();f.panel.setPercent(f.controls,30);f.panel.toggleMute(f.controls);
        check(f.panel.reset(f.controls) && f.controls.state(A).percent()==100 && !f.controls.state(A).muted(),"reset failed");
        check(f.controls.state(B).percent()==20 && f.controls.state(B).muted(),"reset changed another speaker");
    }
    static void zero() {
        Fixture f=new Fixture();f.first();f.panel.setPercent(f.controls,0);
        check(!f.controls.state(A).audible() && !f.controls.state(A).muted(),"zero wrongly toggled mute");
        f.panel.toggleMute(f.controls);check(f.controls.state(A).percent()==0,"mute overwrote zero");
    }
    static void bounds() {
        Fixture f=new Fixture();f.first();Object before=f.controls.snapshot();
        check(!f.panel.setPercent(f.controls,-1) && !f.panel.setPercent(f.controls,101),"invalid percent accepted");
        check(before==f.controls.snapshot(),"invalid edit published state");
    }
    static void noSelection() {
        Fixture f=new Fixture();check(!f.panel.setPercent(f.controls,20) && !f.panel.toggleMute(f.controls)
            && !f.panel.reset(f.controls),"edit without selection accepted");
    }
    static void invalidRow() {
        Fixture f=new Fixture();check(!f.panel.select(-1) && !f.panel.select(2),"bad row accepted");
        check(f.panel.selected()==null,"bad row selected a speaker");
    }
    static void nameSearch() {
        Fixture f=new Fixture();f.panel.filter(" ALiCe ");
        check(f.panel.total()==1 && f.panel.rows().get(0).id.equals(A),"case-insensitive search failed");
    }
    static void uuidSearch() {
        Fixture f=new Fixture();f.panel.filter(B.toString());
        check(f.panel.total()==1 && f.panel.rows().get(0).id.equals(B),"UUID search failed");
    }
    static void hidden() {
        Fixture f=new Fixture();f.first();f.panel.filter("Bob");
        check(f.panel.selected()==null && !f.panel.toggleMute(f.controls),"hidden selection still editable");
    }
    static void paging() {
        Fixture f=new Fixture();f.panel.refresh(f.controls,f.names,-99);
        check(f.panel.rows().size()==1 && f.panel.pageCount()==2,"minimum page bound failed");
        for(int i=0;i<50;i++)f.panel.turnPage(1);
        check(f.panel.pageIndex()==1,"page overflow");f.panel.filter("does not exist");
        check(f.panel.pageIndex()==0 && f.panel.rows().isEmpty(),"filter left invalid page");
        f.panel.filter("");for(int i=0;i<50;i++)f.panel.turnPage(-1);
        check(f.panel.pageIndex()==0,"negative page");
        f.panel.refresh(f.controls,f.names,99);check(f.panel.rows().size()==2,"maximum page bound failed");
    }
    static void pageSelection() {
        Fixture f=new Fixture();f.panel.refresh(f.controls,f.names,1);f.first();f.panel.turnPage(1);
        check(f.panel.selected()==null,"page change retained hidden selection");
    }
    static void sessionChange() {
        Fixture f=new Fixture();f.first();SpeakerControls next=new SpeakerControls();next.observe(A);
        check(!f.panel.setPercent(next,10) && next.state(A).percent()==100,"stale panel changed new session");
    }
    static void sameUuid() {
        Fixture f=new Fixture();f.first();f.panel.setPercent(f.controls,20);SpeakerControls next=new SpeakerControls();next.observe(A);
        f.panel.refresh(next,f.names,5);check(f.panel.selected()==null,"session retained selection");
        check(f.panel.rows().get(0).percent==100 && !f.panel.toggleMute(f.controls),"old session leaked preferences");
    }
    static void disconnect() {
        Fixture f=new Fixture();f.first();f.panel.refresh(null,f.names,5);
        check(f.panel.selected()==null && !f.panel.connected() && f.panel.total()==0,"disconnect did not clear");
        check(!f.panel.reset(f.controls),"disconnected panel edited old session");
    }
    static void rename() {
        Fixture f=new Fixture();f.first();f.names.put(A,"Zulu");f.names.put(B,"Aaron");f.refresh();
        check(f.panel.selected().id.equals(A),"rename changed selected identity");
        f.panel.toggleMute(f.controls);check(f.controls.state(A).muted() && !f.controls.state(B).muted(),"sorted row retargeted mute");
    }
    static void duplicates() {
        Fixture f=new Fixture();f.names.put(B,"Alice");f.refresh();f.panel.select(1);
        check(f.panel.selected().id.equals(B),"duplicate name collapsed identities");
        f.panel.toggleMute(f.controls);check(!f.controls.state(A).muted() && f.controls.state(B).muted(),"duplicate name changed wrong speaker");
    }
    static void immutable() {
        Fixture f=new Fixture();List<SpeakerPanelModel.Row> old=f.panel.rows();f.first();f.panel.setPercent(f.controls,10);
        check(old.get(0).percent==100 && f.panel.selected().percent==10,"published rows were mutated");
        try{old.clear();throw new AssertionError("mutable row list");}catch(UnsupportedOperationException expected){}
    }
    static void sanitize() {
        Fixture f=new Fixture();f.names.put(A,"\u00a7cAlice\n\r\u202e");
        char[] longName=new char[1000];Arrays.fill(longName,'x');f.names.put(B,new String(longName));f.refresh();
        for(SpeakerPanelModel.Row row:f.panel.rows()) {
            check(row.name.length()<=64,"unbounded display name");
            check(row.name.indexOf('\u00a7')<0 && row.name.indexOf('\n')<0 && row.name.indexOf('\u202e')<0,"formatting injection retained");
        }
        f.names.put(A,"\n\r");f.refresh();
        check(f.panel.rows().get(0).name.equals(A.toString().substring(0,8)),"empty sanitized label did not fall back to UUID");
    }
    static void externalMute() {
        Fixture f=new Fixture();f.first();f.controls.update(A,50,true);f.panel.setPercent(f.controls,30);
        check(f.controls.state(A).percent()==30 && f.controls.state(A).muted(),"volume edit reverted latest mute");
    }
    static void externalVolume() {
        Fixture f=new Fixture();f.first();f.controls.update(A,15,false);f.panel.toggleMute(f.controls);
        check(f.controls.state(A).percent()==15 && f.controls.state(A).muted(),"mute reverted latest volume");
    }
    static void nameMap() {
        Fixture f=new Fixture();f.names.put(A,"changed externally");
        check(f.panel.rows().get(0).name.equals("Alice"),"caller changed published names");
        f.refresh();f.panel.filter("changed externally");check(f.panel.rows().get(0).id.equals(A),"fresh name not applied");
    }
}
