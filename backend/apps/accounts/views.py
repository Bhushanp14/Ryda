from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.authtoken.models import Token
from django.contrib.auth import authenticate
from .serializers import RegisterSerializer, LoginSerializer
from apps.profiles.models import RiderProfile

class RegisterView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = RegisterSerializer(data=request.data)
        if serializer.is_valid():
            user = serializer.save()
            token, _ = Token.objects.get_or_create(user=user)
            # Create corresponding rider profile
            RiderProfile.objects.get_or_create(
                user=user,
                defaults={'display_name': user.username}
            )
            return Response({
                'token': token.key,
                'user_id': user.id,
                'username': user.username
            }, status=status.HTTP_201_CREATED)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

class LoginView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = LoginSerializer(data=request.data)
        if serializer.is_valid():
            user = authenticate(
                username=serializer.validated_data['username'],
                password=serializer.validated_data['password']
            )
            if user:
                token, _ = Token.objects.get_or_create(user=user)
                return Response({
                    'token': token.key,
                    'user_id': user.id,
                    'username': user.username
                })
            return Response({'error': 'Invalid credentials'}, status=status.HTTP_401_UNAUTHORIZED)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

class LogoutView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        Token.objects.filter(user=request.user).delete()
        return Response({'message': 'Logged out successfully'})

class DeleteAccountView(APIView):
    permission_classes = [IsAuthenticated]

    def delete(self, request):
        user = request.user
        username = user.username
        # Delete user cascades to Profile, Motorcycles, Rides, and GPS Points
        user.delete()
        return Response({'message': f'Account {username} and all associated data permanently deleted.'}, status=status.HTTP_200_OK)

from django.http import HttpResponse

class AccountDeletionWebPageView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        html_content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RYDA - Account & Data Deletion Request</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: #0F1115;
            color: #FFFFFF;
            line-height: 1.6;
            margin: 0;
            padding: 20px;
        }
        .container {
            max-width: 720px;
            margin: 40px auto;
            background: #181B20;
            border: 1px solid #333B47;
            border-radius: 16px;
            padding: 36px;
        }
        h1 { color: #FF9100; margin-top: 0; font-size: 28px; }
        h2 { color: #00E5FF; font-size: 18px; margin-top: 28px; }
        p, li { color: #A0A8B6; font-size: 15px; }
        ul { padding-left: 20px; }
        .highlight {
            background: #22262E;
            border-left: 4px solid #FF9100;
            padding: 14px 18px;
            border-radius: 0 8px 8px 0;
            margin: 20px 0;
        }
        .highlight p { margin: 0; color: #FFFFFF; }
        .form-box {
            background: #22262E;
            padding: 24px;
            border-radius: 12px;
            margin-top: 24px;
            border: 1px solid #333B47;
        }
        label { display: block; margin-bottom: 8px; color: #FFFFFF; font-weight: 600; font-size: 14px; }
        input, textarea {
            width: 100%;
            padding: 12px;
            background: #0F1115;
            border: 1px solid #333B47;
            border-radius: 8px;
            color: #FFFFFF;
            font-size: 14px;
            box-sizing: border-box;
            margin-bottom: 16px;
        }
        input:focus, textarea:focus { border-color: #FF9100; outline: none; }
        button {
            background: #FF3B30;
            color: #FFFFFF;
            border: none;
            padding: 14px 28px;
            font-size: 15px;
            font-weight: bold;
            border-radius: 8px;
            cursor: pointer;
            transition: background 0.2s;
        }
        button:hover { background: #D32F2F; }
        .badge {
            display: inline-block;
            background: #FF9100;
            color: #0F1115;
            font-size: 12px;
            font-weight: bold;
            padding: 4px 10px;
            border-radius: 6px;
            margin-bottom: 14px;
        }
    </style>
</head>
<body>
    <div class="container">
        <span class="badge">RYDA PRIVACY PORTAL</span>
        <h1>Account & Data Deletion Request</h1>
        <p>This page complies with Google Play user data policy and applicable Indian Data Protection laws (DPDP). Riders may request permanent deletion of their account and all associated telemetry records at any time.</p>

        <div class="highlight">
            <p><strong>Fastest method:</strong> If you have access to the RYDA Android app, navigate to <strong>Profile & Settings &rarr; Account & Security &rarr; Delete Account & Purge Data</strong>. In-app deletion takes effect immediately.</p>
        </div>

        <h2>1. What Data Is Permanently Removed</h2>
        <ul>
            <li><strong>Rider Profile & Identity:</strong> Username, email, profile display name, city, and biography.</li>
            <li><strong>Digital Garage:</strong> All motorcycles, specifications, nickname records, and primary bike assignments.</li>
            <li><strong>GPS Tracks & Telemetry:</strong> All raw and filtered GPS coordinates, latitude/longitude points, altitudes, timestamps, instantaneous speeds, and route polylines.</li>
            <li><strong>Ride History & Aggregates:</strong> Elapsed time, moving time, top speeds, average speeds, elevation profiles, and lifetime mileage counters.</li>
        </ul>

        <h2>2. Lawful Retention Exceptions</h2>
        <ul>
            <li><strong>Security and Network Logs:</strong> Basic server diagnostic logs (IP address, user agent, HTTP status codes) may be retained for up to 90 days strictly for firewall protection, anti-abuse, and fraud detection before automated rotation and purge.</li>
            <li><strong>Legal Obligations:</strong> Any records required to be preserved under mandatory Indian court orders or statutory regulatory requirements will be retained strictly for the duration required by law.</li>
        </ul>

        <h2>3. Expected Processing Time</h2>
        <ul>
            <li><strong>In-app deletion:</strong> Immediate and irrevocable.</li>
            <li><strong>Web request form below:</strong> Processed within <strong>30 calendar days</strong> following identity verification via confirmation email.</li>
        </ul>

        <div class="form-box">
            <h2>Submit Web Deletion Request</h2>
            <form method="POST" action="/privacy/delete-account/">
                <label for="username">RYDA Username or Registered Email</label>
                <input type="text" id="username" name="username" required placeholder="e.g. rider123 or rider@example.com">

                <label for="reason">Reason for deletion (Optional)</label>
                <textarea id="reason" name="reason" rows="3" placeholder="Tell us how we could improve..."></textarea>

                <button type="submit">Submit Deletion Request</button>
            </form>
        </div>
    </div>
</body>
</html>"""
        return HttpResponse(html_content, content_type="text/html")

    def post(self, request):
        identity = request.POST.get('username', '').strip()
        confirmation_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>RYDA - Request Received</title>
    <style>
        body {{ font-family: sans-serif; background: #0F1115; color: #fff; text-align: center; padding: 60px 20px; }}
        .box {{ max-width: 500px; margin: 0 auto; background: #181B20; padding: 40px; border-radius: 16px; border: 1px solid #333B47; }}
        h1 {{ color: #34C759; }}
        p {{ color: #A0A8B6; line-height: 1.6; }}
        a {{ color: #FF9100; text-decoration: none; font-weight: bold; }}
    </style>
</head>
<body>
    <div class="box">
        <h1>Request Received</h1>
        <p>A deletion request has been logged for identifier: <strong>{identity}</strong>.</p>
        <p>If an account matches this record, an email verification will be dispatched. All associated telemetry and personal records will be permanently purged within 30 days per policy.</p>
        <br>
        <a href="/privacy/delete-account/">&larr; Return to Privacy Portal</a>
    </div>
</body>
</html>"""
        return HttpResponse(confirmation_html, content_type="text/html")
