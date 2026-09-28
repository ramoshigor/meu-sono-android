# Meu Sono

Aplicativo Android offline para acompanhar horários e duração do sono. Os registros são mantidos no aparelho; não há conta, anúncios, analytics ou conexão com a internet.

## Recursos

- Cronômetro de sono e registro manual
- Histórico editável, notas e avaliação da qualidade
- Meta diária, médias, regularidade e gráficos
- Lembrete de hora de dormir calculado pela hora de acordar e pela meta
- Tema claro, escuro ou automático
- Acompanhamento opcional em segundo plano com notificação contínua
- Backup e restauração local em JSON; exportação CSV

O aplicativo apresenta tendências dos dados registrados e não faz diagnóstico médico. O lembrete usa alarmes aproximados; o Android pode atrasar notificações por causa da economia de bateria. O modo em segundo plano pode ser encerrado se o usuário forçar a parada do aplicativo.

## Compilar

Abra esta pasta no Android Studio ou use uma instalação local do Gradle compatível com Android Gradle Plugin 8.7.3, Java 17 e Android SDK Platform 35. Para compilar um APK de teste:

```sh
gradle assembleDebug
```

Para compilar a variante de release:

```sh
gradle assembleRelease
```

A variante de release é gerada sem assinatura. O F-Droid assina as compilações distribuídas pelo catálogo oficial. Para instalar um build local, use a variante debug ou assine o APK de release com uma chave sua.

- Application ID: `br.com.meusono.tracker.v12`
- Version name: `1.7.0`
- Version code: `8`
- Android mínimo: 5.0 (API 21)
- Diretório do módulo: `app/`

## Licença

O código-fonte deste projeto é distribuído sob a **GNU General Public License, versão 3.0 somente** (`GPL-3.0-only`). Consulte [`LICENSE`](LICENSE). Os arquivos de interface incluídos neste repositório também fazem parte do projeto e estão sob a mesma licença.

## Instalar pelo F-Droid

O APK oficial do F-Droid terá uma assinatura diferente de APKs instalados por outros canais. Antes de trocar de canal, faça um backup dos registros no aplicativo. A restauração permite importar os dados depois da instalação.
