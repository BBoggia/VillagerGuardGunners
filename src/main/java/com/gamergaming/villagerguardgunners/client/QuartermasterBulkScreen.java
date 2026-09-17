package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Uses the same live container and authority session as the overview. */
public final class QuartermasterBulkScreen extends AbstractContainerScreen<QuartermasterManagementMenu> {
    private final List<Button> rows=new ArrayList<>(),squads=new ArrayList<>(),views=new ArrayList<>();
    private Button policy,profile,replace,assignment,previous,next,preview,apply,clear;
    public QuartermasterBulkScreen(QuartermasterManagementMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=304;imageHeight=228;
    }
    private static Component text(String key,Object... args){return QuartermasterManagement.text("bulk."+key,args);}
    private void request(int action,int value,UUID guard,UUID token){ManagementNetwork.request(menu.requestBulk(action,value,guard,token));}
    private void request(int action,int value){request(action,value,null,null);}
    private Button button(Component label,int x,int y,int width,Runnable action) {
        return addRenderableWidget(Button.builder(label,b->action.run()).bounds(leftPos+x,topPos+y,width,18).build());
    }
    @Override protected void init() {
        super.init();rows.clear();squads.clear();views.clear();
        policy=button(Component.empty(),8,23,142,()->{var s=menu.bulkState();if(s!=null)request(BulkProtocol.POLICY,s.policy()==2?-1:s.policy()+1);});
        profile=button(Component.empty(),154,23,142,()->request(BulkProtocol.PROFILE,0));
        replace=button(Component.empty(),8,43,142,()->{var s=menu.bulkState();if(s!=null)request(BulkProtocol.REPLACE,s.replacement()==1?-1:s.replacement()+1);});
        assignment=button(Component.empty(),154,43,142,()->{var s=menu.bulkState();if(s!=null)request(BulkProtocol.ASSIGN,s.assignment()==1?-1:s.assignment()+1);});
        for(int i=0;i<5;i++) {
            final int row=i;
            rows.add(button(Component.empty(),8,79+i*18,234,()->{var s=menu.bulkState();if(s!=null&&s.view()==0&&row<s.rows().size())request(BulkProtocol.TOGGLE,0,s.rows().get(row).guard(),null);}));
            squads.add(button(text("squad"),244,79+i*18,52,()->{var s=menu.bulkState();if(s!=null&&row<s.rows().size())request(BulkProtocol.SQUAD,0,s.rows().get(row).guard(),null);}));
        }
        previous=button(Component.literal("<"),8,172,20,()->{var s=menu.bulkState();if(s!=null)request(BulkProtocol.PAGE,s.page()-1);});
        next=button(Component.literal(">"),59,172,20,()->{var s=menu.bulkState();if(s!=null)request(BulkProtocol.PAGE,s.page()+1);});
        String[] labels={"guards","changes","needs"};for(int i=0;i<3;i++){final int view=i;views.add(button(text(labels[i]),82+i*72,172,70,()->request(BulkProtocol.VIEW,view)));}
        button(text("back"),8,202,62,()->request(BulkProtocol.CLOSE,0));
        clear=button(text("clear"),74,202,62,()->request(BulkProtocol.CLEAR,0));
        preview=button(text("preview"),140,202,76,()->request(BulkProtocol.PREVIEW,0));
        apply=button(text("apply"),220,202,76,()->{var s=menu.bulkState();if(s!=null&&s.token()!=null)request(BulkProtocol.APPLY,0,null,s.token());});
        preview.setTooltip(Tooltip.create(text("preview_help")));update();
    }
    private String shortText(String s,int width){return font.width(s)<=width?s:font.plainSubstrByWidth(s,width-font.width("…"))+"…";}
    private Component policy(int v){return text(v==-2?"mixed":v==-1?"keep":v==0?"manual":v==1?"restock":"maintain");}
    private Component yesNo(int v){return text(v==-2?"mixed":v==-1?"keep":v==0?"no":"yes");}
    private Component station(int v){return text(v==-2?"mixed":v==-1?"keep":v==0?"auto":"this");}
    private void choice(Button b,String label,Component choice,Component current,Component help) {
        var full=text(label,choice);b.setMessage(Component.literal(shortText(full.getString(),132)));
        b.setTooltip(Tooltip.create(full.copy().append("\n").append(text("current",current)).append("\n").append(help)));
    }
    private void update() {
        var s=menu.bulkState();for(var b:List.of(policy,profile,replace,assignment,clear,preview))b.active=s!=null;
        apply.active=s!=null&&s.token()!=null&&minecraft.level!=null&&minecraft.level.getGameTime()<s.expiresAt();
        previous.active=s!=null&&s.page()>0;next.active=s!=null&&s.page()+1<s.pages();
        for(int i=0;i<views.size();i++)views.get(i).active=s!=null&&s.view()!=i&&(i==0||s.token()!=null);
        for(int i=0;i<5;i++) {
            var b=rows.get(i);var squad=squads.get(i);b.visible=s!=null&&i<s.rows().size();squad.visible=false;if(!b.visible)continue;
            var r=s.rows().get(i);boolean select=s.view()==0;squad.visible=select&&r.squad();
            b.setWidth(squad.visible?234:288);b.setMessage(Component.literal(shortText((select?(r.selected()?"[x] ":"[ ] "):"")+r.label(),b.getWidth()-10)));
            b.setTooltip(Tooltip.create(Component.literal(r.detail())));squad.setTooltip(Tooltip.create(text("squad_help")));
        }
        if(s==null)return;
        choice(policy,"policy",policy(s.policy()),policy(s.currentPolicy()),text("policy_help"));
        choice(profile,"profile",s.profile().isEmpty()?text("keep"):Component.literal(s.profile()),Component.literal(s.currentProfile()),text("profile_help"));
        choice(replace,"replace",yesNo(s.replacement()),yesNo(s.currentReplacement()),text("replace_help"));
        choice(assignment,"assignment",station(s.assignment()),station(s.currentAssignment()),text("assignment_help"));
        preview.active=s.selected()>0;clear.active=s.selected()>0;
        preview.setTooltip(Tooltip.create(text("preview_help").copy().append("\n").append(text("status."+s.status(),s.applied()))));
        apply.setTooltip(Tooltip.create(text("apply_help")));
    }
    @Override protected void containerTick(){super.containerTick();update();}
    @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g);super.render(g,x,y,partial);renderTooltip(g,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff373737);
        g.fill(leftPos+1,topPos+1,leftPos+imageWidth-1,topPos+imageHeight-1,0xffeeeeee);
        g.fill(leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xffc6c6c6);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        g.drawString(font,text("title"),8,8,0x404040,false);var s=menu.bulkState();
        if(s==null){g.drawString(font,text("loading"),8,67,0x404040,false);return;}
        String summary=text("selection",s.selected(),s.excluded()).getString();if(s.limited())summary+=" · "+text("limited").getString();
        g.drawString(font,shortText(summary,288),8,67,0x404040,false);
        g.drawString(font,shortText((s.page()+1)+"/"+s.pages(),29),29,177,0x404040,false);
        var status=text("status."+s.status(),s.applied());
        if(s.view()==2&&s.refreshing())status=text("estimating");
        g.drawString(font,shortText(status.getString(),288),8,191,0x404040,false);
        if(s.rows().isEmpty())g.drawString(font,text(s.view()==0?"empty":s.token()!=null&&s.view()==2?(s.refreshing()?"estimating":"no_needs"):"preview_help_short"),12,90,0x404040,false);
    }
}
