import streamlit as st
from dotenv import load_dotenv
from src.services.onboarding import OnboardingService
from src.domain.models import Speciality

load_dotenv()

st.set_page_config(
    page_title="Over-the-Shoulder Tutor",
    page_icon="🎓",
    layout="wide",
    initial_sidebar_state="expanded"
)

st.markdown("""
<style>
    /* Dark Mode Premium Theme */
    [data-testid="stAppViewContainer"] {
        background-color: #0d1117;
        color: #e6edf3;
        font-family: 'Inter', sans-serif;
    }
    
    [data-testid="stSidebar"] {
        background-color: #161b22;
        border-right: 1px solid #30363d;
    }

    .stChatFloatingInputContainer {
        bottom: 2rem;
    }

    /* Vibrant Gradient Header */
    .title-header {
        background: linear-gradient(90deg, #bb86fc 0%, #3700b3 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        font-weight: 800;
        font-size: 2.5rem;
        margin-bottom: 0.5rem;
    }
    
    .stChatMessage {
        border-radius: 8px;
        padding: 10px;
        background-color: #1a1f26;
        border: 1px solid #30363d;
        margin-bottom: 12px;
        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        transition: transform 0.2s;
    }
    .stChatMessage:hover {
        transform: translateY(-2px);
    }
</style>
""", unsafe_allow_html=True)


def get_service():
    return OnboardingService()

service = get_service()

if "is_solved" not in st.session_state:
    st.session_state.is_solved = False

with st.sidebar:
    st.markdown("### 🛠️ Active Workspace")
    st.markdown("We are silently tracking local files in `target_repo/` via Git Diff.")
    st.info("Start editing `target_repo/main.py`. The Tutor sees exactly what you type!")
    
    st.markdown("### 🎓 Student Profile")
    speciality_input = st.text_input("What is your speciality? (e.g. Auth, DevOps, UI)", value="Auth")
    
    if st.button("Refresh Workspace Context"):
        st.rerun()

    diff = service.tracker.get_git_diff()
    
    if "last_diff" not in st.session_state:
        st.session_state.last_diff = diff
        
    if st.session_state.last_diff != diff:
        st.session_state.is_solved = False
        st.session_state.last_diff = diff

    if diff:
        st.warning("Uncommitted Changes Detected:")
        st.code(diff, language="diff")
    else:
        st.success("No uncommitted changes.")

# Chat Interface
st.markdown('<div class="title-header">Over-the-Shoulder Tutor 🎓</div>', unsafe_allow_html=True)
st.caption("Ask me a question! I have full context of your local codebase and uncommitted changes.")


# Use the input from sidebar
current_speciality = Speciality(speciality=speciality_input)

# Cooldown logic for Hint button
import time
COOLDOWN_SECONDS = 60
if "last_hint_time" not in st.session_state:
    st.session_state.last_hint_time = 0.0

col1, col2, col3 = st.columns([6, 2, 2])
is_solved = st.session_state.is_solved

hint_clicked = False

with col2:
    time_since_hint = time.time() - st.session_state.last_hint_time
    is_cooling_down = time_since_hint < COOLDOWN_SECONDS
    hint_msg = f"💡 Request Hint ({int(COOLDOWN_SECONDS - time_since_hint)}s)" if is_cooling_down else "💡 Request Hint"
    
    if st.button(hint_msg, disabled=is_cooling_down or is_solved, use_container_width=True):
        st.session_state.last_hint_time = time.time()
        
        # 1. Background Verification Check
        with st.spinner("Checking your code first..."):
            result = service.verify_solution(speciality=current_speciality)
        
        if result.get("solved", False):
            # Target Solved!
            st.session_state.is_solved = True
            st.balloons()
            st.success("🎉 **You already solved it!** No hints needed.")
            st.session_state.messages.append({"role": "assistant", "content": "🎉 Congratulations, you've successfully fixed the bug! You have completed this exercise."})
            st.rerun()
        else:
            hint_clicked = True

with col3:
    if st.button("✅ Check Solution", disabled=is_solved, use_container_width=True):
        with st.spinner("Grading your code..."):
            result = service.verify_solution(speciality=current_speciality)
            if result.get("solved", False):
                st.session_state.is_solved = True
                st.balloons()
            
                feedback = result.get("feedback", "You have completed this exercise.")
                st.session_state.messages.append({
                    "role": "assistant", 
                    "content": f"🎉 **Correct!** {feedback}"
                })
                st.rerun()
            else:
                st.error("❌ **Not quite right.** " + result.get("feedback", ""))

# Initialize chat history
if "messages" not in st.session_state:
    st.session_state.messages = []

# Display chat history on app rerun
for message in st.session_state.messages:
    if str(message["content"]).startswith("*(Hidden)"):
        continue # Optional: Hide the automated message trigger from the chat UI
    with st.chat_message(message["role"]):
        st.markdown(message["content"])

# React to user input or hint trigger
prompt_placeholder = "Exercise complete!" if st.session_state.is_solved else "I'm stuck on this auth issue, what should I do?"
prompt = st.chat_input(prompt_placeholder, disabled=st.session_state.is_solved)

if prompt:
    st.chat_message("user").markdown(prompt)
    st.session_state.messages.append({"role": "user", "content": prompt})

    with st.chat_message("assistant"):
        with st.spinner("Analyzing your workspace..."):
            stream = service.process_message(prompt, speciality=current_speciality)
            response = st.write_stream(stream)
        
    st.session_state.messages.append({"role": "assistant", "content": response})

elif hint_clicked:
    st.session_state.messages.append({"role": "user", "content": "*(Hidden) Providing a hint based on active workspace...*"})
    with st.chat_message("assistant"):
        with st.spinner("Analyzing your workspace for a hint..."):
            stream = service.process_message("", speciality=current_speciality, is_hint_trigger=True)
            response = st.write_stream(stream)
            
    st.session_state.messages.append({"role": "assistant", "content": response})
