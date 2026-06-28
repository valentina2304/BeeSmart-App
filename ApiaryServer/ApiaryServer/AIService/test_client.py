import requests
import base64


def run_test(image_path):
    # Mirror the Kotlin PhotoManager behavior (JPEG, Base64)
    with open(image_path, "rb") as f:
        img_base64 = base64.b64encode(f.read()).decode('utf-8')
    
    payload = {"image_base64": f"data:image/jpeg;base64,{img_base64}"}
    
    print("Trimitere cerere... (poate dura ~10 secunde)")
    # Generous timeout because DeepBee processes thousands of cells
    response = requests.post("http://localhost:5000/analyze", json=payload, timeout=60)
    
    if response.status_code == 200:
        print("Rezultat primit de la AI:")
        data = response.json()
        print(data.get("results", data))
    else:
        print("Eroare:", response.text)

if __name__ == "__main__":
    run_test("poza_fagure_test.jpg")  # Provide the path to a real comb photo here