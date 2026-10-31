import uvicorn
import os
import sys

# Tambahkan root folder ke sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

if __name__ == "__main__":
    print("======================================================")
    print("  DENKASIR UMKM - FASTAPI + POSTGRESQL/SQLITE SERVER  ")
    print("======================================================")
    print("  Swagger UI Docs : http://localhost:8000/docs        ")
    print("  ReDoc           : http://localhost:8000/redoc       ")
    print("  REST API Base   : http://localhost:8000/api/v1      ")
    print("======================================================")
    uvicorn.run("backend.app.main:app", host="0.0.0.0", port=8000, reload=True)
