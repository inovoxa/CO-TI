<!--
  Rota: /operacoes/plataforma · Permissão: system:monitor-screen:query
  A saúde da própria plataforma (Kafka, OpenSearch, bordas das unidades) é medida pelo Prometheus e
  mostrada num painel do Grafana, embutido aqui. Configure monitor.urls.grafana com o endereço do painel
  em modo "kiosk" (ex.: https://portal.orgao.gov.br/grafana/d/plataforma?kiosk) no mesmo domínio do portal.
-->
<template>
  <div class="pg">
    <MonitorHeader titulo="Plataforma e escala" fonte="Bordas nas unidades → Kafka → bancos por tipo de dado → portal · painel do Grafana" sem-alertas />

    <el-card v-if="url" shadow="never" :body-style="{ padding: '0' }">
      <iframe :src="url" title="Grafana · saúde da plataforma" class="frame"></iframe>
    </el-card>

    <el-card v-else shadow="never">
      <el-result icon="info" title="O painel do Grafana ainda não foi configurado" sub-title="Informe o endereço do painel em monitor.urls.grafana.">
        <template #extra>
          <div class="ajuda">
            <p><b>O que o painel deve mostrar</b> (todas as métricas existem nos exportadores oficiais para Prometheus):</p>
            <el-table :data="METRICAS" size="small" border>
              <el-table-column prop="o" label="O quê" width="210" />
              <el-table-column prop="p" label="Por quê" />
            </el-table>
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

defineOptions({ name: 'MonitorPlataforma' })

const METRICAS = [
  { o: 'Eventos por segundo por tópico', p: 'Volume entrando no Kafka por tipo (logs, métricas, câmeras); mostra picos e quedas.' },
  { o: 'Atraso dos consumidores (lag)', p: 'Se cresce, alguém não está dando conta: indexação de logs, alertas ou agregação do telão.' },
  { o: 'Bordas conectadas', p: 'Quantas unidades estão enviando; as desconectadas guardam dados localmente e reenviam depois.' },
  { o: 'Disco e heap do OpenSearch', p: 'Logs ocupam a maior parte do armazenamento; avisa antes de lotar.' },
  { o: 'Ingestão de métricas', p: 'Pontos por segundo no VictoriaMetrics/TDengine e séries ativas.' },
  { o: 'Portal (yudao)', p: 'Tempo de resposta, erros 5xx, conexões WebSocket abertas e réplicas no Kubernetes.' }
]

const url = ref('')
onMounted(async () => {
  url.value = (await Api.getConfig()).grafana || ''
})
</script>

<style scoped lang="scss">
.pg { padding: 4px; }
.frame { display: block; width: 100%; height: calc(100vh - 210px); min-height: 520px; border: 0; }
.ajuda { max-width: 760px; text-align: left; font-size: 13px; }
</style>
