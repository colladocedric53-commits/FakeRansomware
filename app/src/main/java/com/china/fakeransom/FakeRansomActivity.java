private void startFakeEncryption() {
    statusText.setText("INITIATING SYSTEM LOCK...");
    
    // 1. Vibrate to get attention
    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
    } else {
        v.vibrate(500);
    }

    final String[] fakeFiles = {"photos.zip", "banking_app.db", "contacts.vcf", "whatsapp_media.mp4"};
    int index = 0;

    Timer timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
            runOnUiThread(() -> {
                progress += 2;
                progressBar.setProgress(progress);
                
                // Cycle through fake file names to look busy
                String currentFile = fakeFiles[index % fakeFiles.length];
                statusText.setText("ENCRYPTING: " + currentFile + "\nProgress: " + progress + "%");
                index++;

                if (progress >= 100) {
                    showRansomNote();
                    this.cancel();
                    // Final long vibration
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                }
            });
        }
    }, 0, 100); 
}

// Override onBackPressed to prevent easy exit during simulation
@Override
public void onBackPressed() {
    Toast.makeText(this, "SYSTEM LOCKED: CANNOT EXIT", Toast.LENGTH_SHORT).show();
          }
