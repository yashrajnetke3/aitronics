package com.aitronics.assistant

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView

val Night = Color(0xFF0C101B)
val Panel = Color(0xFF171D2B)
val Mint = Color(0xFF82EED8)
val Muted = Color(0xFFAAB5C8)
val Edge = Color(0xFF30394A)

@Composable fun Orb(size: Dp, active:Boolean=false) {
    AndroidView(factory={NeonOrb(it)},update={it.awake=active},modifier=Modifier.size(size))
}

@Composable fun AssistantHeader(mode:String, settings:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(Panel,RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center) {
            if(NeonBubble.enabled && (NeonBubble.awake || NeonBubble.pinned && BackgroundWake.running)) Orb(41.dp,NeonBubble.awake)
            else Text("A",color=Mint,fontWeight=FontWeight.Bold,fontSize=23.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("AITRONICS",fontWeight=FontWeight.Bold,letterSpacing=2.sp,fontSize=19.sp,color=Color.White)
            Text(mode,color=Muted,fontSize=12.sp)
        }
        OutlinedButton(onClick=settings,border=BorderStroke(1.dp,Edge),contentPadding=PaddingValues(horizontal=15.dp)) { Text("Settings",color=Muted) }
    }
}

@Composable fun HomePanel(wake:Boolean, phrase:String, camera:()->Unit,battery:()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(
        Brush.linearGradient(listOf(Color(0xFF172D35),Color(0xFF18263D),Color(0xFF251E39))))
        .border(1.dp,Edge,RoundedCornerShape(28.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Orb(150.dp,NeonBubble.awake)}
        Text(if(wake) "Ready when you are" else "How can I help?",color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=28.sp,lineHeight=34.sp)
        Text(if(wake) "Say “$phrase” and give a command." else "Speak a command or type below.",color=Muted,fontSize=15.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=camera,border=BorderStroke(1.dp,Edge)) { Text("Open camera",color=Color.White) }
            OutlinedButton(onClick=battery,border=BorderStroke(1.dp,Edge)) { Text("Battery level",color=Color.White) }
        }
    }
}

@Composable fun ChatMessage(user:Boolean,text:String) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=if(user) Alignment.End else Alignment.Start) {
        Text(if(user) "You" else "Aitronics",color=if(user) Muted else Mint,style=MaterialTheme.typography.labelMedium,
            modifier=Modifier.padding(horizontal=5.dp,vertical=4.dp))
        Surface(shape=RoundedCornerShape(19.dp,19.dp,if(user) 5.dp else 19.dp,if(user) 19.dp else 5.dp),
            color=if(user) Color(0xFF233C49) else Panel) {
            Text(text,Modifier.padding(horizontal=15.dp,vertical=11.dp),color=Color(0xFFEDF2FC),lineHeight=23.sp)
        }
    }
}

@Composable fun BubbleOptions(overlay:Boolean,configure:(Boolean,Boolean)->Unit,permission:()->Unit) {
    Text("Appearance",color=Mint,style=MaterialTheme.typography.titleSmall)
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text("Neon wake bubble",Modifier.weight(1f))
        Switch(modifier=Modifier.semantics { contentDescription="Neon wake bubble" },checked=NeonBubble.enabled,onCheckedChange={configure(it,NeonBubble.pinned)})
    }
    if(NeonBubble.enabled) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){ Text("Keep bubble visible"); Text("While hands-free listening is on",color=Muted,fontSize=12.sp) }
            Switch(modifier=Modifier.semantics { contentDescription="Keep bubble visible" },checked=NeonBubble.pinned,onCheckedChange={configure(true,it)})
        }
        TextButton(onClick=permission) { Text(if(overlay) "Manage display over other apps" else "Allow bubble over other apps") }
    }
}
