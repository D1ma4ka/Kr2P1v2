package ru.krapiva.app.offline

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** All exported text is shown in preview; this component performs no network upload. */
object ShareImages {
    fun render(context: Context, title: String, lines: List<String>, photo: Bitmap? = null): Bitmap {
        val result=Bitmap.createBitmap(1080,1440,Bitmap.Config.ARGB_8888);val canvas=Canvas(result)
        canvas.drawColor(Color.rgb(16,19,21))
        val accent=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(243,175,69)}
        canvas.drawRoundRect(36f,36f,1044f,1404f,32f,32f,Paint().apply{color=Color.rgb(29,34,38)})
        canvas.drawRect(72f,270f,1008f,276f,accent)
        val logo=BitmapFactory.decodeResource(context.resources,ru.krapiva.app.R.drawable.krapiva_logo)
        if(logo!=null){canvas.drawBitmap(logo,null,RectF(72f,60f,772f,60f+700f*logo.height/logo.width),Paint(Paint.ANTI_ALIAS_FLAG));logo.recycle()}
        var y=310f
        fun text(value: String,size:Float,maxLines:Int,color:Int=Color.rgb(244,238,228)) {
            val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
            val layout=StaticLayout.Builder.obtain(value,0,value.length,paint,936).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            canvas.save();canvas.translate(72f,y);layout.draw(canvas);canvas.restore();y+=layout.height+24f
        }
        text(title,52f,2,Color.rgb(243,175,69))
        if(photo!=null){
            val height=300f;val scale=maxOf(936f/photo.width,height/photo.height)
            val sourceW=(936f/scale).toInt().coerceAtLeast(1);val sourceH=(height/scale).toInt().coerceAtLeast(1)
            val left=(photo.width-sourceW)/2;val top=(photo.height-sourceH)/2
            canvas.drawBitmap(photo,Rect(left,top,left+sourceW,top+sourceH),RectF(72f,y,1008f,y+height),Paint(Paint.ANTI_ALIAS_FLAG));y+=height+24f
        }
        lines.take(8).forEach { if(y<1220)text(it,34f,if(photo==null && y<1160)2 else 1) }
        y=1320f;text("KraPiva · личная коллекция",26f,1,Color.rgb(180,180,175))
        return result
    }
    fun cleanup(directory: File, now:Long=System.currentTimeMillis()) {
        directory.listFiles()?.filter{it.isFile&&now-it.lastModified()>86_400_000L}?.forEach{it.delete()}
    }
    fun share(context: Context, bitmap: Bitmap) {
        val directory=File(context.cacheDir,"shares").apply{mkdirs()};cleanup(directory)
        val file=File(directory,"${UUID.randomUUID()}.png")
        try {
            file.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)){"Не удалось создать изображение"}}
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.shares",file)
            val intent=Intent(Intent.ACTION_SEND).apply{
                type="image/png";putExtra(Intent.EXTRA_STREAM,uri)
                clipData=ClipData.newUri(context.contentResolver,"KraPiva",uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent,"Поделиться карточкой KraPiva"))
        } catch(e: Exception){file.delete();throw e}
    }
}
