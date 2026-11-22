package com.gamergaming.villagerguardgunners.client;

import com.gamergaming.villagerguardgunners.menu.QuartermasterManagementMenu;
import com.gamergaming.villagerguardgunners.quartermaster.management.*;
import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Five visible rows within a bounded twenty-row page, fitting a 320x240 GUI viewport. */
public final class QuartermasterManagementScreen extends AbstractContainerScreen<QuartermasterManagementMenu> {
    private final Map<Integer,Button> tabs=new LinkedHashMap<>();
    private final List<Button> rows=new ArrayList<>();
    private Button trade,stock,previous,next,refresh,clear,manage;
    private int scroll,lastView=-1,lastPage=-1;
    public QuartermasterManagementScreen(QuartermasterManagementMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=304;imageHeight=228;
    }
    private static Component text(String key,Object... args){return QuartermasterManagement.text(key,args);}
    private void request(int action,int page,int selection){ManagementNetwork.request(menu.request(action,page,selection));}
    private Button button(Component label,int x,int y,int width,Runnable action) {
        return addRenderableWidget(Button.builder(label,ignored->action.run()).bounds(leftPos+x,topPos+y,width,18).build());
    }
    @Override protected void init() {
        super.init();tabs.clear();rows.clear();
        int[] views={ManagementProtocol.SUPPLIES,ManagementProtocol.NEEDS,ManagementProtocol.GUARDS,ManagementProtocol.SERVICE};
        String[] labels={"stock","needs","guards","service"};
        for(int i=0;i<views.length;i++){int view=views[i];tabs.put(view,button(text(labels[i]),8+i*73,24,69,()->request(view,0,-1)));}
        for(int i=0;i<5;i++){final int index=i;rows.add(button(Component.empty(),8,62+i*18,288,()->{
            var p=page();if(p==null||index+scroll>=p.rows().size())return;var r=p.rows().get(index+scroll);
            if(p.view()==ManagementProtocol.NEEDS&&r.selection()>=0)request(ManagementProtocol.FILTER,0,r.selection());
            if(p.view()==ManagementProtocol.GUARDS&&r.selection()>=0)request(ManagementProtocol.FORGET,0,r.selection());
        }));}
        previous=button(Component.literal("<"),8,174,24,()->{var p=page();if(p!=null)request(p.view(),p.page()-1,-1);});
        next=button(Component.literal(">"),76,174,24,()->{var p=page();if(p!=null)request(p.view(),p.page()+1,-1);});
        refresh=button(text("refresh"),108,174,85,()->request(ManagementProtocol.REFRESH,0,-1));
        clear=button(text("all_guards"),201,174,95,()->request(ManagementProtocol.CLEAR_FILTER,0,-1));
        trade=button(text("trade"),8,202,92,()->request(ManagementProtocol.TRADE,0,-1));
        manage=button(text("bulk.manage"),106,202,92,()->{
            ManagementNetwork.request(menu.requestBulk(BulkProtocol.OPEN,0,null,null));
        });
        stock=button(text("open_stock"),204,202,92,()->request(ManagementProtocol.STOCK,0,-1));update();
    }
    private OverviewProtocol.Page page(){var p=menu.details();var s=menu.summary();return p!=null&&s!=null&&p.revision()==s.revision()?p:null;}
    private String shorten(String value,int width){return font.width(value)<=width?value:font.plainSubstrByWidth(value,Math.max(0,width-font.width("…")))+"…";}
    private Component tooltip(OverviewProtocol.Row row) {
        var value=Component.literal(row.label()+"\n"+row.detail());
        if(!row.status().isEmpty())value.append("\n").append(status(row.status()));
        if(row.kind()==OverviewProtocol.GUARD_ROW&&row.selection()>=0)value.append("\n").append(text("forget_help"));
        if(row.kind()==OverviewProtocol.STOCK_ROW)value.append("\n").append(text("stock_amounts",row.available(),row.requested(),row.missing()));
        if(row.kind()==OverviewProtocol.NEED_ROW) {
            value.append("\n").append(text("need_amounts",row.requested(),row.available(),row.incoming(),row.missing()));
            if(row.incoming()>0)value.append("\n").append(text("incoming_help"));
            for(var reason:ServiceNeeds.Reason.values())if((row.flags()&(1<<reason.ordinal()))!=0)value.append("\n").append(text("reason."+reason.name().toLowerCase(Locale.ROOT)));
            if(row.selection()>=0)value.append("\n").append(text("filter_help"));
        }
        return value;
    }
    private Component status(String status){return status.equals("ready")?text("guard_ready"):status.equals("unavailable")?text("unavailable"):Component.translatable("message.villagerguardgunners.service."+status);}
    private void update() {
        var s=menu.summary();var p=page();
        for(var e:tabs.entrySet())e.getValue().active=s!=null&&s.stock()&&(e.getKey()==ManagementProtocol.SUPPLIES||s.manage())&&s.tab()!=e.getKey();
        manage.active=s!=null&&s.manage();
        trade.active=s!=null&&s.tradeStatus()==ManagementProtocol.READY;stock.active=s!=null&&s.stock();
        if(s!=null)trade.setTooltip(Tooltip.create(text(switch(s.tradeStatus()) {
            case ManagementProtocol.READY -> "trade_help";case ManagementProtocol.BUSY -> "trade_busy";
            case ManagementProtocol.UNSAFE -> "trade_unsafe";case ManagementProtocol.EMPTY_HAND -> "trade_empty_hand";default -> "trade_missing";
        })));
        stock.setTooltip(Tooltip.create(text(s!=null&&s.stock()?"stock_help":"stock_denied")));
        if(p!=null&&(p.view()!=lastView||p.page()!=lastPage)){scroll=0;lastView=p.view();lastPage=p.page();}
        scroll=Math.min(scroll,p==null?0:Math.max(0,p.rows().size()-5));
        for(int i=0;i<rows.size();i++) {
            var b=rows.get(i);b.visible=p!=null&&s.stock()&&scroll+i<p.rows().size();if(!b.visible)continue;
            var r=p.rows().get(scroll+i);String right=switch(r.kind()) {
                case OverviewProtocol.STOCK_ROW -> r.available()+" / "+r.requested();
                case OverviewProtocol.NEED_ROW -> (r.flags()&((1<<ServiceNeeds.Reason.CONDITIONAL.ordinal())|(1<<ServiceNeeds.Reason.UNAVAILABLE.ordinal())|(1<<ServiceNeeds.Reason.DEFINITION_UNAVAILABLE.ordinal())|(1<<ServiceNeeds.Reason.PROFILE_MISSING.ordinal())))!=0
                        ?text("unknown").getString():(r.flags()&(1<<ServiceNeeds.Reason.PROTECTED.ordinal()))!=0?text("protected").getString():r.requested()+" / "+r.available()+" / "+r.incoming()+" / "+r.missing();
                case OverviewProtocol.GUARD_ROW -> (r.selection()>=0?text("forget"):status(r.status())).getString();default -> "+"+r.available();
            };
            right=shorten(right,123);b.setMessage(Component.literal(shorten(r.label(),267-font.width(right))+"  "+right));
            b.setTooltip(Tooltip.create(tooltip(r)));
        }
        previous.active=p!=null&&p.page()>0;next.active=p!=null&&p.page()+1<p.pages();
        refresh.active=s!=null&&s.stock();clear.active=s!=null&&s.manage()&&s.tab()==ManagementProtocol.GUARDS;
        var help=text("refresh_help").copy();if(p!=null&&p.restricted())help.append("\n").append(text("restricted_help"));if(p!=null&&p.truncated())help.append("\n").append(text("truncated_help"));
        refresh.setTooltip(Tooltip.create(help));clear.setTooltip(Tooltip.create(text("all_guards_help")));
    }
    @Override public boolean mouseScrolled(double x,double y,double amount) {
        var p=page();if(p!=null&&x>=leftPos+8&&x<leftPos+296&&y>=topPos+62&&y<topPos+152) {
            scroll=Math.max(0,Math.min(Math.max(0,p.rows().size()-5),scroll+(amount<0?1:-1)));update();return true;
        }return super.mouseScrolled(x,y,amount);
    }
    @Override protected void containerTick(){super.containerTick();update();}
    @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g);super.render(g,x,y,partial);renderTooltip(g,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff373737);
        g.fill(leftPos+1,topPos+1,leftPos+imageWidth-1,topPos+imageHeight-1,0xffeeeeee);
        g.fill(leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xffc6c6c6);
        g.fill(leftPos+8,topPos+60,leftPos+296,topPos+154,0xffededed);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        g.drawString(font,title,8,8,0x404040,false);var s=menu.summary();var p=page();
        if(s==null){paragraph(g,text("loading"),65);return;}
        if(!s.stock()){paragraph(g,text("visitor"),65);return;}
        if(p==null){paragraph(g,text("refreshing"),65);return;}
        var station=ServiceStatus.values()[p.stationStatus()];
        String returns=text("returns_short",s.freeReturns()).getString();
        g.drawString(font,shorten(text("station_label",Component.translatable(station.translationKey()).getString()).getString(),280-font.width(returns)),8,47,0x303030,false);
        g.drawString(font,returns,296-font.width(returns),47,0x303030,false);
        if(p.rows().isEmpty())paragraph(g,text(p.refreshing()?"refreshing":p.view()==ManagementProtocol.SERVICE?"no_history":p.view()==ManagementProtocol.GUARDS?"no_guards":p.view()==ManagementProtocol.NEEDS?"no_needs":"no_stock"),68);
        long age=minecraft==null||minecraft.level==null?0:Math.max(0,(minecraft.level.getGameTime()-p.observedAt())/20);
        String coverage=text("coverage",p.inspected(),p.unavailable(),age).getString();
        if(p.truncated()||p.restricted())coverage+=" · "+text("limited").getString();
        if(p.refreshing())coverage+=" · "+text("refreshing").getString();
        g.drawString(font,shorten(coverage,288),8,158,0x404040,false);
        g.drawString(font,(p.page()+1)+"/"+p.pages(),38,179,0x404040,false);
        if(p.rows().size()>5)g.drawString(font,(scroll+1)+"–"+Math.min(scroll+5,p.rows().size())+"/"+p.rows().size(),239,8,0x404040,false);
    }
    private void paragraph(GuiGraphics g,Component value,int y){for(var line:font.split(value,272)){if(y>143)break;g.drawString(font,line,16,y,0x303030,false);y+=11;}}
}
