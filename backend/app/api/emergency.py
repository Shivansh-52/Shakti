import os
import shutil
import smtplib
import logging
from datetime import datetime
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.base import MIMEBase
from email import encoders
from typing import Optional

from fastapi import APIRouter, UploadFile, File, Form, BackgroundTasks, HTTPException
from app.db.mongodb import db_manager

logger = logging.getLogger(__name__)

router = APIRouter()

UPLOAD_DIR = os.path.join(os.getcwd(), "uploads", "sos_videos")
os.makedirs(UPLOAD_DIR, exist_ok=True)

from app.core.config import settings

# SMTP Configuration
SMTP_HOST = settings.SMTP_HOST
SMTP_PORT = settings.SMTP_PORT
SMTP_USER = settings.SMTP_USER
SMTP_PASSWORD = settings.SMTP_PASSWORD


def send_email_in_background(
    recipient_email: str,
    subject: str,
    body_text: str,
    video_path: Optional[str] = None,
    file_name: Optional[str] = None
):
    try:
        if not recipient_email or "@" not in recipient_email:
            logger.warning(f"Invalid recipient email: {recipient_email}")
            return

        msg = MIMEMultipart()
        msg["From"] = SMTP_USER if SMTP_USER else "noreply@surakshasetu.org"
        msg["To"] = recipient_email
        msg["Subject"] = subject

        msg.attach(MIMEText(body_text, "plain"))

        if video_path and os.path.exists(video_path):
            with open(video_path, "rb") as f:
                part = MIMEBase("application", "octet-stream")
                part.set_payload(f.read())
            encoders.encode_base64(part)
            part.add_header(
                "Content-Disposition",
                f'attachment; filename="{file_name if file_name else os.path.basename(video_path)}"',
            )
            msg.attach(part)

        # If SMTP credentials are configured, send via live SMTP
        if SMTP_USER and SMTP_PASSWORD:
            clean_user = SMTP_USER.strip()
            clean_pass = SMTP_PASSWORD.strip().replace(" ", "")
            try:
                # Try Port 465 SSL first (universally allowed across all ISPs)
                server = smtplib.SMTP_SSL(SMTP_HOST, 465, timeout=20)
                server.login(clean_user, clean_pass)
                server.send_message(msg)
                server.quit()
                logger.info(f"[SUCCESS] Emergency SOS video email successfully sent via SSL (465) to {recipient_email}")
                print(f"[SUCCESS] Emergency SOS video email successfully sent via SSL (465) to {recipient_email}")
            except Exception as e_ssl:
                logger.warning(f"Port 465 SSL failed ({e_ssl}), attempting Port 587 STARTTLS...")
                server = smtplib.SMTP(SMTP_HOST, 587, timeout=20)
                server.starttls()
                server.login(clean_user, clean_pass)
                server.send_message(msg)
                server.quit()
                logger.info(f"[SUCCESS] Emergency SOS video email successfully sent via TLS (587) to {recipient_email}")
                print(f"[SUCCESS] Emergency SOS video email successfully sent via TLS (587) to {recipient_email}")
        else:
            logger.info(f"[INFO] Simulated automated email dispatch to {recipient_email} (Configure SMTP_USER & SMTP_PASSWORD for direct live SMTP relay).")
    except Exception as e:
        logger.error(f"[ERROR] Failed to send emergency email: {e}", exc_info=True)


@router.post("/send-video-email")
async def send_video_email(
    background_tasks: BackgroundTasks,
    recipient_email: str = Form(...),
    user_name: Optional[str] = Form("Citizen"),
    user_phone: Optional[str] = Form(""),
    maps_link: Optional[str] = Form(""),
    message: Optional[str] = Form(""),
    video_file: Optional[UploadFile] = File(None)
):
    saved_video_path = None
    saved_filename = None

    if video_file and video_file.filename:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        saved_filename = f"SOS_{timestamp}_{video_file.filename}"
        saved_video_path = os.path.join(UPLOAD_DIR, saved_filename)

        with open(saved_video_path, "wb") as buffer:
            shutil.copyfileobj(video_file.file, buffer)
        logger.info(f"Stored emergency video: {saved_video_path}")

    # Construct body
    subject = f"🚨 URGENT SOS: Emergency Video & Live Location - {user_name}"
    body = f"""🚨 [SURAKSHA SETU / SHAKTI AUTOMATED EMERGENCY ALERT]

Distress Call from: {user_name}
Phone: {user_phone if user_phone else 'Not provided'}
Distress Status: ACTIVE EMERGENCY (Immediate Response Required)
Live GPS Location: {maps_link if maps_link else 'Location coordinates broadcasted'}

Alert Message: {message if message else 'I am in danger! Automated SOS trigger with video recording evidence.'}

Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

An emergency video recorded automatically during distress is attached with this email.
"""

    # Record incident in database
    try:
        if db_manager.db is not None:
            await db_manager.db["sos_incidents"].insert_one({
                "recipient_email": recipient_email,
                "user_name": user_name,
                "user_phone": user_phone,
                "maps_link": maps_link,
                "message": message,
                "video_file": saved_filename,
                "created_at": datetime.utcnow(),
                "status": "DISPATCHED"
            })
    except Exception as e:
        logger.warning(f"Could not persist incident to mongo: {e}")

    # Send email in background
    background_tasks.add_task(
        send_email_in_background,
        recipient_email=recipient_email,
        subject=subject,
        body_text=body,
        video_path=saved_video_path,
        file_name=saved_filename
    )

    return {
        "status": "success",
        "message": f"Emergency SOS alert and video queued for automatic dispatch to {recipient_email}",
        "recipient": recipient_email,
        "video_file": saved_filename
    }
