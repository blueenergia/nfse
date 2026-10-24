# ⚡ Blue NFSe Microservice (Docker + Cloudflare Tunnel)

Microserviço em Java 17 (Javalin + SQLite) containerizado para emissão automatizada de NFS-e (Padrão Nacional SEFIN) integrado ao **ERP ASSOCIADOS** da Blue Energia.

---

## 🏗️ Arquitetura

1. O **ERP ASSOCIADOS** envia uma requisição `POST /api/nfse/emitir` com o `idLancamento`, dados do associado e valor da fatura.
2. O microserviço:
   * Calcula a potência equivalente em **kWp**: $\text{kWp} = \frac{\text{metaKwh}}{30 \times \text{HSP} \times (1 - \text{perdas}/100)}$.
   * Calcula o **Valor do Serviço (30% de taxa de intermediação)**: os outros 70% não são tributados (contribuição para associação).
   * Valida obrigatoriedade do **endereço completo** do associado.
   * Monta e assina a DPS Nacional com o Certificado Digital A1 da Blue.
   * Transmite para a SEFIN Nacional (Homologação ou Produção).
   * Registra a transação no banco **SQLite local** para auditoria.
   * Retorna **`numeroNota`**, `chaveAcesso` e `valorEmitido` em JSON.
3. O **ERP ASSOCIADOS** recebe o retorno e atualiza o lançamento no Firestore.

---

## 🚀 Como Iniciar no Docker

### 1. Configurar variáveis e certificados
1. Entre na pasta `server`:
   ```bash
   cd server
   cp .env.example .env
   ```
2. Coloque os seus certificados na pasta `server/certs/`:
   * `certificado.pfx`
   * `cacerts.jks`
3. Edite o `.env` preenchendo as senhas e o token do Cloudflare Tunnel:
   ```env
   CERTIFICADO_SENHA=sua_senha
   CADEIA_CERTIFICADOS_SENHA=senha_cacerts
   CLOUDFLARE_TUNNEL_TOKEN=ey...
   API_KEY=sua_chave_secreta
   ```

### 2. Subir com Docker Compose
```bash
docker compose up -d --build
```

Verifique os logs:
```bash
docker compose logs -f nfse-service
```

---

## 📡 Endpoints da API

### `GET /health`
Healthcheck para monitoramento.

### `POST /api/nfse/emitir`
Emite a NFS-e. Requer header `X-API-KEY: sua_chave`.

**Payload Exemplo enviado pelo ERP:**
```json
{
  "idLancamento": "lanc_98321",
  "associado": {
    "cpfCnpj": "48752851320",
    "nome": "VITURIANO BORGES FERNANDES",
    "email": "vitor.itz@hotmail.com",
    "telefone": "99992236291",
    "endereco": {
      "logradouro": "R SAO DOMINGOS",
      "numero": "285-A",
      "bairro": "PARQUE SAO JOSE",
      "codigoMunicipio": "2105302",
      "uf": "MA",
      "cep": "65905370"
    }
  },
  "consumoKwh": 1431,
  "valorTotalPago": 763.87
}
```

**Resposta de Sucesso (200 OK):**
```json
{
  "sucesso": true,
  "idLancamento": "lanc_98321",
  "numeroNota": "70",
  "chaveAcesso": "21053022262967464000129000000000007026100884591336",
  "numeroDps": 218,
  "serieDps": "70000",
  "valorEmitido": 229.16,
  "valorTotalPago": 763.87,
  "kwpCalculado": 11.925,
  "dataProcessamento": "2026-10-24T17:15:00-03:00"
}
```

---

## 💻 Integração no ERP ASSOCIADOS (TypeScript)

Snippet pronto para usar no seu backend Node/Express ou serviço de lançamentos:

```typescript
import axios from 'axios';
import { getFirestore } from 'firebase-admin/firestore';

const db = getFirestore();

interface AssociadoNfseDTO {
  cpfCnpj: string;
  nome: string;
  email?: string;
  telefone?: string;
  endereco: {
    logradouro: string;
    numero: string;
    complemento?: string;
    bairro: string;
    codigoMunicipio: string; // IBGE (ex: 2105302)
    uf: string;
    cep: string;
  };
}

export async function emitirNfseParaLancamento(
  idLancamento: string,
  associado: AssociadoNfseDTO,
  consumoKwh: number,
  valorTotalPago: number
) {
  const NFSE_URL = process.env.NFSE_SERVICE_URL || 'https://nfse.blueenergia.com';
  const API_KEY = process.env.NFSE_API_KEY || 'blue-nfse-secret-key';

  try {
    const response = await axios.post(
      `${NFSE_URL}/api/nfse/emitir`,
      {
        idLancamento,
        associado,
        consumoKwh,
        valorTotalPago
      },
      {
        headers: {
          'Content-Type': 'application/json',
          'X-API-KEY': API_KEY
        },
        timeout: 45000 // Timeout de 45 segundos para comunicação SEFIN
      }
    );

    const dadosNota = response.data;

    // Atualiza o lançamento no Firestore com o número da nota emitido
    await db.collection('lancamentos').doc(idLancamento).update({
      nfseEmitida: true,
      nfseNumero: dadosNota.numeroNota,
      nfseChaveAcesso: dadosNota.chaveAcesso,
      nfseValorEmitido: dadosNota.valorEmitido,
      nfseKwp: dadosNota.kwpCalculado,
      nfseEmitidaEm: new Date()
    });

    return dadosNota;
  } catch (error: any) {
    const erroMsg = error.response?.data?.mensagem || error.message;
    console.error(`Erro ao emitir NFS-e para lançamento ${idLancamento}:`, erroMsg);
    throw new Error(`Falha na NFS-e: ${erroMsg}`);
  }
}
```
