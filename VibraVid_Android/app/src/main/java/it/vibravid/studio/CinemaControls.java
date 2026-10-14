package it.vibravid.studio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.media3.common.C;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;
import java.util.Locale;

/** Cinema chrome is independent of the video surface and respects reduced motion. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class CinemaControls extends FrameLayout {
    interface Actions {
        void back(); void play(); void seek(long position); void jump(long amount);
        void tracks(); void speed(); void fit(); void gallery(); void pip();
    }
    private final Actions actions;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final FrameLayout chrome;
    private final LinearLayout top,bottom,center,toolbar;
    private final TextView current,remaining,status,hud;
    private final DefaultTimeBar timeline;
    private final Icon play,lock,unlock;
    private final ProgressBar buffering;
    private final boolean reducedMotion;
    private Typeface font;
    private final TextView[] captions=new TextView[5];
    private final Icon[] icons=new Icon[5];
    private boolean visible=true,locked,playing,scrubbing,pip;
    private long duration=C.TIME_UNSET;
    private final Runnable hide=()->{if(playing&&!scrubbing)hideControls();};
    private final Runnable hideHud=this::dismissHud;
    private final Runnable hideUnlock=this::dismissUnlock;

    CinemaControls(Context context,String title,String subtitle,boolean reducedMotion,Actions actions){
        super(context);this.actions=actions;this.reducedMotion=reducedMotion;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        chrome=new FrameLayout(context);addView(chrome,new LayoutParams(-1,-1));
        View scrim=new View(context){final Paint paint=new Paint();float edge;Shader upper,lower;
            @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);edge=Math.min(dp(190),height*.43f);upper=new LinearGradient(0,0,0,edge,0xd9000000,Color.TRANSPARENT,Shader.TileMode.CLAMP);lower=new LinearGradient(0,height-edge,0,height,Color.TRANSPARENT,0xeb000000,Shader.TileMode.CLAMP);}
            @Override protected void onDraw(Canvas canvas){
            paint.setShader(upper);canvas.drawRect(0,0,getWidth(),edge,paint);
            paint.setShader(lower);canvas.drawRect(0,getHeight()-edge,getWidth(),getHeight(),paint);
        }};chrome.addView(scrim,new LayoutParams(-1,-1));
        top=new LinearLayout(context);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(8),dp(8),0);
        chrome.addView(top,new LayoutParams(-1,dp(80),Gravity.TOP));
        Icon back=icon("back","Torna alla libreria","player-back",v->actions.back());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout titles=new LinearLayout(context);titles.setOrientation(LinearLayout.VERTICAL);titles.setPadding(dp(10),0,dp(10),0);
        TextView name=text(title==null?"Video":title,18,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(name);
        status=text(AppLanguage.text(context,subtitle),11,false);status.setTextColor(0xffb5b7bb);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(status);
        top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        lock=icon("lock","Blocca i controlli","player-lock",v->setLocked(true));top.addView(lock,new LinearLayout.LayoutParams(dp(48),dp(48)));
        center=new LinearLayout(context);center.setGravity(Gravity.CENTER);
        chrome.addView(center,new LayoutParams(-2,dp(88),Gravity.CENTER));
        Icon rewind=icon("rewind","Indietro di 10 secondi","player-rewind",v->{actions.jump(-10000);notice("−10 secondi");});
        Icon forward=icon("forward","Avanti di 10 secondi","player-forward",v->{actions.jump(10000);notice("+10 secondi");});
        play=icon("pause","Pausa","player-play",v->actions.play());play.large=true;
        center.addView(rewind,new LinearLayout.LayoutParams(dp(64),dp(64)));LinearLayout.LayoutParams main=new LinearLayout.LayoutParams(dp(88),dp(88));main.setMargins(dp(24),0,dp(24),0);center.addView(play,main);center.addView(forward,new LinearLayout.LayoutParams(dp(64),dp(64)));
        bottom=new LinearLayout(context);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(20),0,dp(20),dp(14));
        chrome.addView(bottom,new LayoutParams(-1,-2,Gravity.BOTTOM));
        LinearLayout progress=new LinearLayout(context);progress.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);progress.setGravity(Gravity.CENTER_VERTICAL);
        current=text("0:00",12,false);remaining=text("−0:00",12,false);remaining.setGravity(Gravity.END);
        current.setMinWidth(dp(48));remaining.setMinWidth(dp(48));progress.addView(current,new LinearLayout.LayoutParams(-2,dp(48)));
        current.setGravity(Gravity.CENTER_VERTICAL);remaining.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        timeline=new DefaultTimeBar(context);timeline.setTag("player-timeline");timeline.setContentDescription(AppLanguage.text(getContext(),"Avanzamento del video"));
        timeline.setPlayedColor(0xfff4f4f5);timeline.setScrubberColor(Color.WHITE);timeline.setBufferedColor(0xff777a80);timeline.setUnplayedColor(0xff3b3e45);
        progress.addView(timeline,new LinearLayout.LayoutParams(0,dp(48),1));progress.addView(remaining,new LinearLayout.LayoutParams(-2,dp(48)));bottom.addView(progress);
        timeline.addListener(new TimeBar.OnScrubListener(){
            @Override public void onScrubStart(TimeBar bar,long position){scrubbing=true;handler.removeCallbacks(hide);preview(position);}
            @Override public void onScrubMove(TimeBar bar,long position){preview(position);}
            @Override public void onScrubStop(TimeBar bar,long position,boolean cancelled){scrubbing=false;if(!cancelled)actions.seek(position);interacted();}
        });
        toolbar=new LinearLayout(context);toolbar.setGravity(Gravity.CENTER);
        String[] names={"Audio","Velocità","Adatta","Salva","Mini player"};String[] tags={"tracks","speed","fit","gallery","pip"};
        String[] symbols={"tracks","speed","fit","save","pip"};
        for(int i=0;i<5;i++){
            final int index=i;LinearLayout button=new LinearLayout(context);button.setOrientation(LinearLayout.VERTICAL);button.setGravity(Gravity.CENTER);button.setTag("player-"+tags[i]);button.setContentDescription(AppLanguage.text(context,names[i]));
            if(i==0)button.setContentDescription(AppLanguage.text(getContext(),"Audio e sottotitoli"));button.setBackground(ripple());button.setFocusable(true);button.setOnClickListener(v->{interacted();switch(index){case 0:actions.tracks();break;case 1:actions.speed();break;case 2:actions.fit();break;case 3:actions.gallery();break;default:actions.pip();}});
            icons[i]=new Icon(context,symbols[i]);icons[i].setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);button.addView(icons[i],new LinearLayout.LayoutParams(dp(24),dp(24)));
            captions[i]=text(AppLanguage.text(context,names[i]),10,false);captions[i].setSingleLine(true);captions[i].setGravity(Gravity.CENTER);LinearLayout.LayoutParams label=new LinearLayout.LayoutParams(-1,-2);label.topMargin=dp(6);button.addView(captions[i],label);
            toolbar.addView(button,new LinearLayout.LayoutParams(0,dp(56),1));
        }bottom.addView(toolbar);
        unlock=icon("lock","Sblocca i controlli","player-unlock",v->setLocked(false));unlock.setBackground(pill(0xcc202126));LayoutParams un=new LayoutParams(dp(52),dp(52),Gravity.END|Gravity.CENTER_VERTICAL);un.rightMargin=dp(20);addView(unlock,un);unlock.setVisibility(GONE);
        hud=text("",15,true);hud.setGravity(Gravity.CENTER);hud.setPadding(dp(20),dp(12),dp(20),dp(12));hud.setBackground(pill(0xd9202126));LayoutParams hp=new LayoutParams(-2,-2,Gravity.CENTER_HORIZONTAL|Gravity.TOP);hp.topMargin=dp(94);addView(hud,hp);hud.setVisibility(GONE);
        buffering=new ProgressBar(context);buffering.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));addView(buffering,new LayoutParams(dp(38),dp(38),Gravity.CENTER));buffering.setVisibility(GONE);
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,float size,boolean bold){TextView text=new TextView(getContext());text.setText(value);text.setTextSize(size);text.setTextColor(0xfff4f4f5);text.setFontFeatureSettings("tnum");
        if(font==null)try{font=Typeface.createFromAsset(getContext().getAssets(),"fonts/nunito-sans.ttf");}catch(RuntimeException ignored){font=Typeface.create("sans-serif",Typeface.NORMAL);}text.setTypeface(Typeface.create(font,bold?Typeface.BOLD:Typeface.NORMAL));return text;}
    private android.graphics.drawable.Drawable pill(int color){GradientDrawable background=new GradientDrawable();background.setColor(color);background.setCornerRadius(dp(24));return background;}
    private android.graphics.drawable.Drawable ripple(){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),null,pill(Color.WHITE));}
    private Icon icon(String symbol,String description,String tag,View.OnClickListener click){Icon icon=new Icon(getContext(),symbol);icon.setContentDescription(AppLanguage.text(getContext(),description));icon.setTag(tag);icon.setFocusable(true);icon.setBackground(ripple());icon.setOnClickListener(v->{interacted();click.onClick(v);});return icon;}
    private void preview(long position){current.setText(time(position));remaining.setText("−"+time(Math.max(0,duration-position)));}
    static String time(long value){long seconds=Math.max(0,value)/1000;return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    void update(long position,long length,long buffered,boolean ready,boolean isBuffering){
        playing=ready;duration=length;
        play.symbol=ready?"pause":"play";play.setContentDescription(AppLanguage.text(getContext(),ready?"Pausa":"Riproduci"));play.invalidate();
        timeline.setEnabled(length>0&&length!=C.TIME_UNSET);timeline.setDuration(length>0?length:0);timeline.setBufferedPosition(Math.max(0,buffered));
        if(!scrubbing){timeline.setPosition(Math.max(0,position));current.setText(time(position));remaining.setText(length>0?"−"+time(Math.max(0,length-position)):"--:--");}
        buffering.setVisibility(isBuffering&&!pip?VISIBLE:GONE);center.setVisibility(isBuffering?INVISIBLE:VISIBLE);
        if(!ready&&!locked&&!pip)showControls();
    }
    void speedLabel(float speed){captions[1].setText(String.format(Locale.ROOT,"%s×",speed==Math.round(speed)?Integer.toString(Math.round(speed)):Float.toString(speed)));}
    void fitLabel(boolean fill){captions[2].setText(AppLanguage.text(getContext(),fill?"Riempi":"Adatta"));}
    void exportStatus(String message,int progress){status.setText(AppLanguage.text(getContext(),message)+(progress>=0?" · "+progress+"%":""));}
    void pipAvailable(boolean available){toolbar.getChildAt(4).setVisibility(available?VISIBLE:GONE);}
    void notice(String message){if(pip)return;hud.setText(AppLanguage.text(getContext(),message));hud.setVisibility(VISIBLE);handler.removeCallbacks(hideHud);handler.postDelayed(hideHud,1500);}
    private void dismissHud(){hud.setVisibility(GONE);}
    private void dismissUnlock(){if(locked)unlock.setVisibility(GONE);}
    boolean locked(){return locked;}
    boolean shown(){return visible;}
    void interacted(){handler.removeCallbacks(hide);if(playing&&!locked&&!pip)handler.postDelayed(hide,3500);}
    void toggle(){if(locked){unlock.setVisibility(VISIBLE);handler.removeCallbacks(hideUnlock);handler.postDelayed(hideUnlock,3500);notice("Schermo bloccato");return;}if(visible)hideControls();else showControls();}
    void showControls(){if(locked||pip)return;visible=true;chrome.animate().cancel();chrome.setVisibility(VISIBLE);chrome.setAlpha(1f);interacted();}
    void hideControls(){if(!visible)return;visible=false;handler.removeCallbacks(hide);chrome.animate().cancel();
        if(!reducedMotion&&ValueAnimator.areAnimatorsEnabled())chrome.animate().alpha(0f).setDuration(160).withEndAction(()->{if(!visible)chrome.setVisibility(GONE);}).start();else{chrome.setAlpha(0);chrome.setVisibility(GONE);}}
    void setLocked(boolean value){locked=value;if(value){hideControls();unlock.setVisibility(VISIBLE);handler.postDelayed(hideUnlock,3500);notice("Schermo bloccato");}else{handler.removeCallbacks(hideUnlock);unlock.setVisibility(GONE);showControls();}}
    void pip(boolean value){pip=value;handler.removeCallbacks(hideHud);hud.setVisibility(GONE);unlock.setVisibility(GONE);buffering.setVisibility(GONE);if(value){chrome.animate().cancel();chrome.setVisibility(GONE);visible=false;handler.removeCallbacks(hide);}else if(!locked)showControls();else toggle();}
    void suspend(){handler.removeCallbacksAndMessages(null);chrome.animate().cancel();}
    void safeInsets(int left,int topInset,int right,int bottomInset){chrome.setPadding(left,topInset,right,bottomInset);}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);boolean compact=height<dp(330);
        top.getLayoutParams().height=dp(compact?64:80);top.requestLayout();
        center.getLayoutParams().height=dp(compact?60:88);center.setTranslationY(compact?-dp(12):0);center.requestLayout();
        for(int i=0;i<center.getChildCount();i++){View child=center.getChildAt(i);child.getLayoutParams().width=dp(i==1?(compact?64:88):(compact?48:64));child.getLayoutParams().height=dp(i==1?(compact?64:88):(compact?48:64));child.requestLayout();}
        for(int i=0;i<5;i++){captions[i].setVisibility(compact?GONE:VISIBLE);toolbar.getChildAt(i).getLayoutParams().height=dp(compact?48:56);toolbar.getChildAt(i).requestLayout();}
        bottom.setPadding(dp(width<dp(380)?12:24),0,dp(width<dp(380)?12:24),dp(compact?4:16));bottom.requestLayout();
        LayoutParams hp=(LayoutParams)hud.getLayoutParams();hp.topMargin=dp(compact?66:94);hud.setLayoutParams(hp);
    }
    /** Small code-native icons keep all controls sharp at every density. */
    private final class Icon extends View {
        String symbol;boolean large;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
        Icon(Context context,String symbol){super(context);this.symbol=symbol;}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float size=Math.min(getWidth(),getHeight());float side=large?size*.44f:Math.min(dp(26),size*.6f);
            canvas.save();canvas.translate((getWidth()-side)/2,(getHeight()-side)/2);canvas.scale(side/24,side/24);
            paint.setColor(Color.WHITE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);
            path.reset();switch(symbol){
                case "back":path.moveTo(14,5);path.lineTo(7,12);path.lineTo(14,19);canvas.drawPath(path,paint);break;
                case "play":paint.setStyle(Paint.Style.FILL);path.moveTo(7,3);path.lineTo(21,12);path.lineTo(7,21);path.close();canvas.drawPath(path,paint);break;
                case "pause":paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(5,3,9,21,1,1,paint);canvas.drawRoundRect(15,3,19,21,1,1,paint);break;
                case "rewind":case "forward":
                    if(symbol.equals("forward")){canvas.translate(24,0);canvas.scale(-1,1);}canvas.drawArc(2,3,22,23,195,290,false,paint);path.moveTo(1,7);path.lineTo(3,1);path.lineTo(7,5);canvas.drawPath(path,paint);if(symbol.equals("forward")){canvas.scale(-1,1);canvas.translate(-24,0);}
                    paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(9);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("10",12,16,paint);break;
                case "lock":canvas.drawRoundRect(5,10,19,22,2,2,paint);canvas.drawArc(8,2,16,17,180,180,false,paint);canvas.drawLine(12,15,12,18,paint);break;
                case "save":canvas.drawLine(12,2,12,15,paint);path.moveTo(7,10);path.lineTo(12,15);path.lineTo(17,10);canvas.drawPath(path,paint);path.reset();path.moveTo(3,15);path.lineTo(3,21);path.lineTo(21,21);path.lineTo(21,15);canvas.drawPath(path,paint);break;
                case "fit":for(int i=0;i<4;i++){canvas.save();canvas.rotate(i*90,12,12);path.reset();path.moveTo(3,9);path.lineTo(3,3);path.lineTo(9,3);canvas.drawPath(path,paint);canvas.restore();}break;
                case "pip":canvas.drawRoundRect(2,4,22,20,2,2,paint);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(12,12,20,18,1,1,paint);break;
                case "speed":canvas.drawArc(2,3,22,23,150,240,false,paint);canvas.drawLine(12,13,17,8,paint);canvas.drawCircle(12,13,1,paint);break;
                default:canvas.drawRoundRect(2,4,22,20,3,3,paint);canvas.drawLine(6,10,10,10,paint);canvas.drawLine(14,10,18,10,paint);canvas.drawLine(6,15,18,15,paint);
            }canvas.restore();
        }
    }
}
