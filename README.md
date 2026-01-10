# JADE Multi-Agent Ecosystem

## Descriere
Sistem multi-agent descentralizat implementat în JADE (Java) cu integrare OpenAI (GPT-4o). Ecosistemul conține agenți de chat cu interfață grafică (GUI) pentru comunicare P2P și agenți de serviciu specializați: `Oracle` (asistent general), `Vision` (analiză imagini) și `Summarizer` (rezumate text). Funcționalitățile includ persistența istoricului (logs), protocol FIPA-ACL și securizare API prin `.env`.

## Instalare și Execuție
1. **Clonare:** `git clone https://github.com/jc008titan/SI-Proiect`
2. **Setup:** Importă proiectul în Eclipse și adaugă `jade.jar` la *Build Path*.
3. **Configurare:** Creează un fișier `.env` în folderul rădăcină care să conțină: `OPENAI_API_KEY=sk-cheia-ta-aici`.
4. **Rulare:** Creează o configurație de rulare cu Main Class `jade.Boot` și următoarele argumente:
   
   `-gui -agents agent1:chatproject.ChatAgent;agent2:chatproject.ChatAgent;agent3:chatproject.ChatAgent;chatgpt:chatproject.GeneralAiAgent;vision:chatproject.VisionAgent;summarizer:chatproject.SummarizerAgent`