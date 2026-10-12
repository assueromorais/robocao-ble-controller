# Protocolo BLE do robô cão

Documentado por engenharia reversa do bytecode Dalvik do app original, para fins de
interoperabilidade. Nenhum código do app original foi reaproveitado.

## Visão geral

O robô **não usa conexão GATT**. Toda a comunicação é feita por **anúncios BLE** (advertising):

```
Celular ──(anúncio: comando)──▶ Robô       (só de ida, reenviado a cada 300 ms)
Celular ◀──(anúncio: resposta)── Robô      (só no pareamento)
```

Consequência prática: não existe "conexão" de verdade. O app não tem como saber se o robô
desligou depois de pareado.

## Anúncio enviado pelo celular

| Campo | Valor |
|---|---|
| Tipo de AD | Manufacturer Specific Data |
| Manufacturer ID | `0x1100` |
| Payload | `0x07` + pacote de 11 bytes |
| Modo / potência | `LOW_LATENCY` / `TX_POWER_HIGH`, connectable, sem timeout |

### Pacote de 11 bytes

| Byte | Conteúdo |
|---|---|
| 0 | Header (depende da família e do tipo: pareamento ou comando) |
| 1–3 | `appId`: 3 bytes aleatórios (1–254) gerados uma vez por celular |
| 4–6 | `robotId`: recebido do robô no pareamento (zeros no pacote de pareamento) |
| 7 | Contador (0–254) |
| 8 | Código do comando |
| 9 | Checksum: soma dos bytes 0–8, truncada em 8 bits |
| 10 | `0xAA` (terminador) |

### Famílias de robô

O app original trata seis modelos, que caem em duas famílias de protocolo:

| Família | Modelos | Header pareamento | Header resposta | Header comando |
|---|---|---|---|---|
| A | 66005, 66006, 66008 | `0xD0` | `0xD1` | `0xE0` |
| B | 66009, 66010, 66011 | `0xD5` | `0xD6` | `0xE5` |

O Cão Robô Travesso (FansiKids / UCC Life, versão com garra) é família B, provavelmente o modelo 66010.

## Pareamento

1. O celular anuncia `[header_pareamento, appId, 0, 0, 0, 0, 0, checksum, 0xAA]`.
2. O celular escaneia. O robô responde com um anúncio cujo scan record cru tem:
   - `bytes[2] == header_resposta`
   - `bytes[3..5] == appId` (a resposta é endereçada a este celular)
   - `bytes[6..8] = robotId`
3. A partir daí, todo comando leva o `robotId`.

## Contador

O robô usa o contador para distinguir um comando novo de uma repetição:

- O contador **só avança quando o comando muda** (segurar "frente" reenvia o mesmo pacote).
- No **modo programação** ele é **forçado** a avançar a cada passo, para o robô gravar passos
  repetidos (frente, frente) como dois passos.

## Comandos (família B)

| Código | Ação | Código | Ação |
|---|---|---|---|
| 0 | Parar | 10 | Deitar |
| 1 | Frente | 11 | Rastejar |
| 2 | Trás | 12 | Espreguiçar |
| 3 | Esquerda | 13 | Nadar |
| 4 | Direita | 14 | Andar em pé |
| 5 | **Garra** (modelo 66010) | 15 | Carinho |
| 6 | Música | 17 | Pata para trás |
| 7 | Bambolê | 18 | Patrulha |
| 8 | História | 19 | Pausar |
| 9 | Sentar | 65–77 | Comandos de voz do app original (sentar, pata, dançar…) |

Na família B os códigos 16–19 são ações, por isso as diagonais do controle ficam desativadas.
O código 16 inclusive **entra no modo programação**.

## Modo programação

Equivale aos botões "mouse" (entrar) e "123" (executar) do controle físico:

```
16 (entrar no modo, o robô diz "entro no modo de programação")
 0 (reset)
 passo 1, passo 2, … (até 16, contador forçado)
20 (executar, o robô diz "executo o programa")
 0
```

Família A: entrar = 39, executar = 55, passos com códigos 81–95.

**Bug encontrado no app original:** no modelo 66010 o passo "Carinho" enviava o código 5
(garra). O app corrigido envia 15, como os outros modelos.
