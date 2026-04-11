import asyncio
from src.services.onboarding import OnboardingService
from src.domain.models import Speciality

service = OnboardingService()
speciality = Speciality(speciality="auth")
try:
    stream = service.process_message("", speciality=speciality, is_hint_trigger=True)
    for chunk in stream:
        pass
    print("Success")
except Exception as e:
    import traceback
    traceback.print_exc()
