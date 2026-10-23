<!--
  Rota: /operacoes/mensagens · Permissão: system:monitor-screen:query
  Mostra o Element (cliente do Matrix) dentro do portal. O Element precisa estar publicado no MESMO
  domínio do portal (ex.: https://portal.orgao.gov.br/element/ via proxy reverso), senão o navegador
  bloqueia a moldura. Configure monitor.urls.element no application.yaml.
-->
<template>
  <div class="pg">
    <MonitorHeader titulo="Mensagens" fonte="Matrix Synapse + Element · spaces e salas criados a partir da estrutura do órgão" sem-alertas>
      <template #acoes>
        <el-button v-if="url" tag="a" :href="url" target="_blank" rel="noopener">Abrir em nova aba</el-button>
      </template>
    </MonitorHeader>

    <el-card v-if="url" shadow="never" :body-style="{ padding: '0' }">
      <iframe :src="url" title="Element · mensagens do órgão" class="frame" allow="clipboard-write; microphone; camera; display-capture"></iframe>
    </el-card>

    <el-card v-else shadow="never">
      <el-result icon="info" title="O Element ainda não foi configurado" sub-title="Publique o Element no mesmo domínio do portal e informe o endereço em monitor.urls.element.">
        <template #extra>
          <div class="ajuda">
            <p><b>Como fica a árvore no Element</b> (criada pelo script <code>integracoes/matrix/sincronizar_synapse.py</code>):</p>
            <pre>▸ Diretoria de Tecnologia e Inteligência
   # Avisos gerais
   ▸ Centro de Operações   ! NOC · alertas  # SOC  # Plantão de TI
   ▸ Sede · Datacenter principal   # Geral  # um por departamento
   ▸ Regional …            # Geral  # Atendimento  # Administrativo  # Suporte</pre>
            <p>O bot do portal posta as ocorrências críticas em <code>#noc-alertas</code> (MatrixNotifier).</p>
          </div>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as Api from '@/api/monitor'
import MonitorHeader from '../components/MonitorHeader.vue'

defineOptions({ name: 'MonitorMensagens' })

const url = ref('')
onMounted(async () => {
  url.value = (await Api.getConfig()).element || ''
})
</script>

<style scoped lang="scss">
.pg { padding: 4px; }
.frame { display: block; width: 100%; height: calc(100vh - 210px); min-height: 520px; border: 0; border-radius: 4px; }
.ajuda { max-width: 680px; text-align: left; font-size: 13px; }
pre { padding: 10px 12px; overflow-x: auto; border-radius: 6px; background: var(--el-fill-color-light); font-size: 12px; }
code { font-size: 12px; }
</style>
