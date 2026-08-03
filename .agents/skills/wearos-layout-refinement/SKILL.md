---
name: wearos-layout-refinement
description: Refines and converts generic Jetpack Compose UI components into native, circular-optimized Wear OS Material components.
---

# Skill: Wear OS Jetpack Compose Layout Refinement

Esta skill documenta as diretrizes de engenharia de interface e UX/UI para aplicativos Jetpack Compose rodando em telas circulares do Wear OS (como Galaxy Watch 7). Use-a sempre que novas telas, diálogos ou componentes forem introduzidos no aplicativo.

---

## 1. Diretrizes de Posicionamento Circular (Circular Display Design)

Telas redondas apresentam sérios problemas de corte de conteúdo nas bordas laterais (bezel). Componentes comuns de smartphone (`Column`, `LazyColumn`, `LazyRow`, `Toast`) não devem ser usados diretamente.

### Regras de Ouro:
1. **TimeText no Topo:**
   - Nunca adicione rótulos de texto estáticos para a hora. Use `TimeText()` dentro do parâmetro `timeText` de um `Scaffold` do Wear OS. Ele curva o texto de hora de acordo com a tela do relógio.
2. **Barra de Rolagem Curva (PositionIndicator):**
   - Sempre utilize `PositionIndicator(scalingLazyListState = listState)` dentro do parâmetro `positionIndicator` do `Scaffold`. Isso exibe o scroll curvo semântico.
3. **Escala de Foco (ScalingLazyColumn):**
   - Substitua qualquer lista ou coluna rolável por `ScalingLazyColumn`.
   - Adicione espaçamento vertical superior e inferior (`contentPadding`) de no mínimo `28.dp` a `32.dp` para garantir que o primeiro e último itens da lista fiquem legíveis quando rolados para o centro.
4. **Cards Arredondados:**
   - Use `AppCard` ou `TitleCard` com `Modifier.fillMaxWidth(0.9f)` para que o texto não atinja os cantos retos do visor e faça quebra de linha correta.
5. **Diálogos de Alerta Nativos:**
   - Substitua Toasts e popups retangulares por `androidx.wear.compose.material.dialog.Dialog` hospedando um `Alert` do Wear OS. Passe o botão como `positiveButton` e o texto explicativo no bloco de `content`.

---

## 2. Exemplos de Componentes Semânticos

### 2.1 Botão PTT (Push-To-Talk) Circular Centralizado
```kotlin
Button(
    onClick = { /* Ação */ },
    colors = ButtonDefaults.buttonColors(
        backgroundColor = MaterialTheme.colors.primary
    ),
    modifier = Modifier.size(72.dp) // Destaque central
) {
    Text("PTT", fontSize = 14.sp, fontWeight = FontWeight.Bold)
}
```

### 2.2 Chip de Status de Conexão no Topo
```kotlin
CompactChip(
    onClick = { /* Reconectar */ },
    label = { Text("CONECTADO", fontSize = 9.sp) },
    colors = ChipDefaults.chipColors(
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f),
        contentColor = MaterialTheme.colors.primary
    )
)
```

### 2.3 Rádio Row de Botões Secundários (📍 GPS, 🔋 Bateria)
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(0.9f),
    horizontalArrangement = Arrangement.SpaceEvenly
) {
    CompactButton(
        onClick = { /* Enviar GPS */ },
        modifier = Modifier.size(36.dp)
    ) {
        Text("📍")
    }
}
```
