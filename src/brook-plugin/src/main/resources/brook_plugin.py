import argparse
import json

parser = argparse.ArgumentParser()
parser.add_argument("--mode", required=True)
parser.add_argument("--specialty", required=True)
parser.add_argument("--file", default="")
args = parser.parse_args()

if args.mode == "inject":
    # Devuelve una modificación de prueba
    result = [
        {
            "file": "main.py",
            "content": "# Brook injected this\ndef add(a, b):\n    return a - b  # bug: debería ser +\n"
        }
    ]
    print(json.dumps(result))

elif args.mode == "hint":
    result = {"hint": "Revisa la operación aritmética en la función add()."}
    print(json.dumps(result))