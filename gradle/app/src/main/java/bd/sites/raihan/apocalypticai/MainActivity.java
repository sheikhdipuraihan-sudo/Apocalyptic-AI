package bd.sites.raihan.apocalypticai;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    final int PURPLE=Color.rgb(99,91,255), INK=Color.rgb(28,29,36), MUTED=Color.rgb(105,106,117), BORDER=Color.rgb(232,232,238);
    LinearLayout messages; TextView modelStatus; EditText input;
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String text,float size,int color){ TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); t.setPadding(dp(0),dp(0),dp(0),dp(0)); return t; }
    GradientDrawable bg(int color,float r){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }
    TextView button(String label){ TextView b=tv(label,13,INK); b.setGravity(Gravity.CENTER); b.setPadding(dp(14),dp(9),dp(14),dp(9)); b.setBackground(bg(Color.WHITE,22)); b.setOnClickListener(v->{}); return b; }
    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(Color.WHITE); getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); build(); }
    void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE); root.setPadding(dp(18),dp(10),dp(18),0);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=tv("✦  apocalyptic ai",19,INK); brand.setTypeface(null,1); top.addView(brand,new LinearLayout.LayoutParams(0,dp(52),1));
        TextView donate=button("Donate"); donate.setTextColor(PURPLE); donate.setOnClickListener(v->openDonate()); top.addView(donate); root.addView(top);
        LinearLayout status=new LinearLayout(this); status.setOrientation(LinearLayout.VERTICAL); status.setPadding(dp(16),dp(14),dp(16),dp(14)); status.setBackground(bg(Color.rgb(247,247,252),18));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); TextView dot=tv("●",12,Color.rgb(37,180,112)); row.addView(dot);
        modelStatus=tv("  LLaMA 3.2 1B  ·  local only",13,INK); row.addView(modelStatus,new LinearLayout.LayoutParams(0,dp(24),1));
        TextView load=button("Load model"); load.setTextColor(PURPLE); load.setOnClickListener(v->pickModel()); row.addView(load); status.addView(row);
        TextView hint=tv("Your prompts stay on this device. Add a GGUF model to enable full local inference.",12,MUTED); hint.setPadding(0,dp(5),0,0); status.addView(hint); root.addView(status);
        HorizontalScrollView chips=new HorizontalScrollView(this); chips.setHorizontalScrollBarEnabled(false); LinearLayout chipRow=new LinearLayout(this); chipRow.setPadding(0,dp(14),0,dp(10));
        for(String s:new String[]{"Water & shelter","First aid","Food safety","Tools & repairs"}){ TextView c=button(s); c.setTextColor(MUTED); c.setBackground(bg(Color.rgb(248,248,250),20)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(38)); p.setMargins(0,0,dp(8),0); chipRow.addView(c,p); c.setOnClickListener(v->send(((TextView)v).getText().toString())); } chips.addView(chipRow); root.addView(chips);
        ScrollView scroll=new ScrollView(this); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(0,dp(6),0,dp(14)); scroll.addView(messages); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addMessage("assistant","I’m your offline field guide. Ask about water, shelter, first aid, food safety, or practical repairs. I’ll keep advice cautious and legal.");
        LinearLayout composer=new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(0,dp(10),0,dp(12));
        input=new EditText(this); input.setHint("Ask your local field guide…"); input.setTextSize(15); input.setSingleLine(false); input.setMaxLines(3); input.setPadding(dp(15),dp(11),dp(10),dp(11)); input.setBackground(bg(Color.rgb(247,247,249),25)); composer.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));
        TextView send=tv("↑",25,Color.WHITE); send.setGravity(Gravity.CENTER); send.setBackground(bg(PURPLE,28)); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(52),dp(52)); sp.setMargins(dp(8),0,0,0); composer.addView(send,sp); send.setOnClickListener(v->send(input.getText().toString())); root.addView(composer); setContentView(root);
    }
    void send(String q){ if(q==null||q.trim().isEmpty())return; addMessage("user",q.trim()); input.setText(""); ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0); addMessage("assistant",answer(q)); }
    void addMessage(String who,String text){ LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(15),dp(12),dp(15),dp(12)); box.setBackground(bg(who.equals("user")?Color.rgb(245,244,255):Color.rgb(248,248,249),18)); TextView label=tv(who.equals("user")?"You":"Apocalyptic AI",11,who.equals("user")?PURPLE:MUTED); label.setTypeface(null,1); box.addView(label); TextView body=tv(text,15,INK); body.setPadding(0,dp(5),0,0); box.addView(body); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,dp(10)); messages.addView(box,p); }
    String answer(String q){ String s=q.toLowerCase(Locale.US); if(s.contains("weapon")||s.contains("gun")||s.contains("explosive")||s.contains("trap")) return "I can’t help build weapons, explosives, or injury traps. I can help with safer alternatives: sturdy shelter, water storage, signaling, protective gear, repair tools, and de-escalation planning."; if(s.contains("water")||s.contains("drink")) return "Start with sealed water if available. For uncertain water, use a reliable filter plus boiling or another validated treatment. Store treated water covered, and avoid any source near fuel, chemicals, or sewage."; if(s.contains("shelter")||s.contains("fire")||s.contains("warm")) return "Choose a dry, stable location away from flood paths and damaged structures. Vent any stove outdoors; carbon monoxide has no smell. Insulate from the ground and keep an exit clear."; if(s.contains("first")||s.contains("injury")||s.contains("bleed")) return "Call local emergency services when possible. For severe bleeding, apply firm direct pressure with clean material and keep the person warm. Don’t remove embedded objects; seek trained medical help."; if(s.contains("food")||s.contains("plant")||s.contains("mushroom")) return "Only eat food you can identify with high confidence and that has been stored safely. Unknown plants and mushrooms are not worth the risk. When in doubt, throw it out."; if(s.contains("repair")||s.contains("tool")||s.contains("cord")) return "A small repair kit goes far: cordage, tape, work gloves, pliers, a multi-bit driver, sewing supplies, containers, and a headlamp. Keep tools organized and practice before an emergency."; return "I’m running in compact offline guide mode. Try asking about water, shelter, first aid, food safety, or tools. Load a compatible LLaMA 3.2 1B GGUF model to use full local generation."; }
    void pickModel(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/octet-stream"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,42); }
    @Override protected void onActivityResult(int r,int c,Intent d){ super.onActivityResult(r,c,d); if(r==42&&c==RESULT_OK&&d!=null){ modelStatus.setText("  LLaMA 3.2 1B  ·  model selected locally"); addMessage("assistant","Model file selected. The compact build keeps the adapter offline; connect a compatible llama.cpp JNI runner for full GGUF inference."); }}
    void openDonate(){ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://raihan.sites.bd"))); }
}
