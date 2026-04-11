from langchain.tools import tool
import os

BASE_DIR = "target_repo/"

def safe_path(path: str) -> str:
    path = path.strip()
    
    # Prevent absolute paths from discarding the BASE_DIR join
    if path.startswith("/"):
        path = path[1:]
        
    # Strip base directory prefixes if the agent included them
    while path.startswith(BASE_DIR):
        path = path[len(BASE_DIR):]
    while path.startswith("target-repo/"):
        path = path[len("target-repo/"):]
        
    full = os.path.abspath(os.path.join(BASE_DIR, path))
    if not full.startswith(os.path.abspath(BASE_DIR)):
        raise ValueError("Ruta fuera del workspace")
    return full


@tool
def read_file(path: str) -> str:
    """Lee un archivo del proyecto."""
    path = safe_path(path)
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


@tool
def write_full_file(file_path: str, content: str) -> str:
    """
    Escribe el contenido completo en un archivo. 
    Úsalo para crear archivos nuevos o modificar existentes enviando todo el código.
    """
    try:
        path = safe_path(file_path)
        
        # Crear directorios si no existen
        os.makedirs(os.path.dirname(path), exist_ok=True)

        # Backup si el archivo ya existe
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                old_content = f.read()
            with open(path + ".bak", "w", encoding="utf-8") as f:
                f.write(old_content)

        # Escritura total
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
            
        return "SUCCESS: Archivo actualizado correctamente"
    except Exception as e:
        return f"ERROR: {str(e)}"