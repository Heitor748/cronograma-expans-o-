# Cronograma Culto Expansão

Site simples (single-page, HTML/CSS/JS puro) para controlar o cronograma do Culto Expansão.

## Como usar

Abra o arquivo `index.html` em qualquer navegador — não precisa de servidor nem instalação.

Ou publique em qualquer host estático (GitHub Pages, Netlify, Vercel, Cloudflare Pages).

## Funcionalidades

- Cadastrar cultos com data, horário, tema, pregador, dirigente e observações.
- Cada culto já vem com uma estrutura padrão de momentos (Boas-vindas, Louvor, Avisos, Mensagem, Ministração) que você pode editar livremente.
- Adicionar, editar e remover momentos dentro de cada culto, com horário e responsável.
- Filtro por mês e busca por texto (tema, pregador, responsável etc.).
- Modo claro/escuro.
- Dados salvos no navegador (localStorage).
- Exportar / importar o cronograma em JSON (para backup ou compartilhar com a equipe).

## Observações

Como os dados ficam no `localStorage` do navegador, cada dispositivo tem seu próprio cronograma.
Para compartilhar com outra pessoa, use **Exportar** e envie o arquivo `.json`; a pessoa usa **Importar** no navegador dela.
