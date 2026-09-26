package com.example.shaketosave;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class SOSAccessibilityService extends AccessibilityService {

    private long firstMediaPressTime = 0;
    private int mediaPressCount = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used, we only care about physical button presses
    }

    @Override
    public void onInterrupt() {
        // Not used
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int keyCode = event.getKeyCode();
            
            // Check for ANY media button from Bluetooth earbuds
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                
                long currentTime = System.currentTimeMillis();
                
                // If it's been more than 3 seconds since the first press, reset the count
                if (currentTime - firstMediaPressTime > 3000) {
                    mediaPressCount = 1;
                    firstMediaPressTime = currentTime;
                } else {
                    mediaPressCount++;
                }

                // If media button is pressed 3 times within 3 seconds
                if (mediaPressCount >= 3) {
                    // Trigger SOS!
                    Intent intent = new Intent(this, ShakeService.class);
                    intent.setAction(ShakeService.ACTION_START_COUNTDOWN);
                    startService(intent);
                    
                    // Reset count
                    mediaPressCount = 0;
                    firstMediaPressTime = 0;
                }
                
                // Return false so music still plays if it's just a single tap
                return false;
            }
        }
        
        return super.onKeyEvent(event);
    }
}
