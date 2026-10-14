package it.vibravid.studio;

import android.media.MediaCodec;
import androidx.media3.common.*;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.datasource.*;
import androidx.media3.extractor.*;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.muxer.Muxer;
import androidx.media3.transformer.InAppMuxer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Writes and reparses real encoded H.264/AAC samples with the production MP4 muxer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class GalleryMuxerTest {
    @Test public void generatedMp4PreservesEncodedVideoAndAudio()throws Exception{
        byte[] fixture;
        try(InputStream in=getClass().getResourceAsStream("/media3-sample.mp4");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            assertNotNull(in);byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);fixture=out.toByteArray();
        }
        Map<Integer,Track> original=extract(fixture);assertEquals(2,original.size());
        File output=new File(RuntimeEnvironment.getApplication().getCacheDir(),"muxer-roundtrip.mp4");
        Muxer muxer=new InAppMuxer.Factory.Builder().build().create(output.getAbsolutePath());
        try{
            List<Sample> samples=new ArrayList<>();
            for(Track track:original.values()){
                assertTrue(MimeTypes.VIDEO_H264.equals(track.format.sampleMimeType)||MimeTypes.AUDIO_AAC.equals(track.format.sampleMimeType));
                assertTrue(track.samples.size()>5);track.token=muxer.addTrack(track.format);samples.addAll(track.samples);
            }
            // Encoded B frames must retain decoding order; sorting by PTS would corrupt it.
            for(Sample sample:samples){MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();info.set(0,sample.data.length,sample.time,(sample.flags&C.BUFFER_FLAG_KEY_FRAME)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0);muxer.writeSampleData(sample.track.token,ByteBuffer.wrap(sample.data.clone()),info);}
        }finally{muxer.close();}
        assertTrue(output.length()>fixture.length/2);
        Map<Integer,Track> saved=extract(Files.readAllBytes(output.toPath()));assertEquals(2,saved.size());
        for(Track source:original.values()){
            Track target=null;for(Track track:saved.values())if(Objects.equals(source.format.sampleMimeType,track.format.sampleMimeType))target=track;
            assertNotNull(target);assertEquals(source.format.width,target.format.width);assertEquals(source.format.height,target.format.height);
            assertEquals(source.format.sampleRate,target.format.sampleRate);assertEquals(source.format.channelCount,target.format.channelCount);
            assertEquals(source.samples.size(),target.samples.size());
            for(int i=0;i<source.samples.size();i++)assertArrayEquals(source.samples.get(i).data,target.samples.get(i).data);
        }
    }
    static Map<Integer,Track> extract(byte[] bytes)throws Exception{
        Map<Integer,Track> tracks=new LinkedHashMap<>();Mp4Extractor extractor=new Mp4Extractor();
        extractor.init(new ExtractorOutput(){public TrackOutput track(int id,int type){Track track=new Track();tracks.put(id,track);return track;}public void endTracks(){}public void seekMap(SeekMap map){}});
        ByteArrayDataSource source=new ByteArrayDataSource(bytes);source.open(new DataSpec(android.net.Uri.parse("https://fixture.invalid/sample.mp4")));
        DefaultExtractorInput input=new DefaultExtractorInput(source,0,bytes.length);PositionHolder position=new PositionHolder();int result,attempts=0;
        do{
            result=extractor.read(input,position);
            if(result==Extractor.RESULT_SEEK){source.close();source.open(new DataSpec.Builder().setUri("https://fixture.invalid/sample.mp4").setPosition(position.position).build());input=new DefaultExtractorInput(source,position.position,bytes.length);}
            if(++attempts>10000)throw new IOException("Extractor loop");
        }while(result!=Extractor.RESULT_END_OF_INPUT);
        source.close();extractor.release();return tracks;
    }
    static final class Sample{final Track track;final byte[] data;final long time;final int flags;Sample(Track track,byte[] data,long time,int flags){this.track=track;this.data=data;this.time=time;this.flags=flags;}}
    static final class Track implements TrackOutput {
        Format format;Muxer.TrackToken token;final List<Sample> samples=new ArrayList<>();final ByteArrayOutputStream pending=new ByteArrayOutputStream();
        @Override public void format(Format value){format=value;}
        @Override public int sampleData(DataReader input,int length,boolean allowEndOfInput,int part)throws IOException{
            byte[] bytes=new byte[length];int count=input.read(bytes,0,length);if(count==-1){if(allowEndOfInput)return -1;throw new EOFException();}pending.write(bytes,0,count);return count;
        }
        @Override public void sampleData(ParsableByteArray input,int length,int part){byte[] bytes=new byte[length];input.readBytes(bytes,0,length);pending.write(bytes,0,length);}
        @Override public void sampleMetadata(long time,int flags,int size,int offset,CryptoData crypto){byte[] bytes=pending.toByteArray();int end=bytes.length-offset;samples.add(new Sample(this,Arrays.copyOfRange(bytes,end-size,end),time,flags));pending.reset();if(offset>0)pending.write(bytes,end,offset);}
    }
}
