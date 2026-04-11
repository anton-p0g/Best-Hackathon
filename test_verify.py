from src.services.onboarding import OnboardingService
from src.domain.models import Speciality

service = OnboardingService()
speciality = Speciality(speciality="auth")
res = service.verify_solution(speciality)
print(res)
