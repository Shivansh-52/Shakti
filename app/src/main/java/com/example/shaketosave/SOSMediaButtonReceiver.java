package com.example.shaketosave;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class SOSMediaButtonReceiver extends BroadcastReceiver {

    private static long firstMediaPressTime = 0;
    private static int mediaPressCount = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                int keyCode = event.getKeyCode();
                
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                    keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                    
                    long currentTime = System.currentTimeMillis();
                    
                    if (currentTime - firstMediaPressTime > 3000) {
                        mediaPressCount = 1;
                        firstMediaPressTime = currentTime;
                    } else {
                        mediaPressCount++;
                    }

                    if (mediaPressCount >= 3) {
                        Intent startIntent = new Intent(context, ShakeService.class);
                        startIntent.setAction(ShakeService.ACTION_START_COUNTDOWN);
                        context.startService(startIntent);
                        
                        mediaPressCount = 0;
                        firstMediaPressTime = 0;
                    }
                }
            }
        }
    }
}
