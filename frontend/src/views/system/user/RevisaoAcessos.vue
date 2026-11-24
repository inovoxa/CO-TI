<!-- Revisão de acessos: o que vale olhar de tempos em tempos, com registro na auditoria de quem revisou. -->
<template>
  <div v-loading="carregando">
    <ContentWrap>
      <div class="flex flex-wrap items-center justify-between gap-8px">
        <div class="text-13px">
          <template v-if="r?.ultimaRevisao">
            Última revisão: <b>{{ quando(r.ultimaRevisao) }}</b> por {{ r.revisadoPor || '—' }}.
          </template>
          <template v-else>Nenhuma revisão registrada ainda.</template>
          <span class="ajuda">
            Recomendado: a cada 3 meses, ou quando alguém muda de função ou sai.</span
          >
        </div>
        <div class="flex gap-8px">
          <el-button @click="carregar"
            ><Icon class="mr-4px" icon="ep:refresh" /> Atualizar</el-button
          >
          <el-button v-hasPermi="['system:user:update']" type="primary" @click="revisado">
            <Icon class="mr-4px" icon="ep:circle-check" /> Marcar como revisado
          </el-button>
        </div>
      </div>
    </ContentWrap>

    <template v-if="r">
      <ContentWrap v-for="g in GRUPOS" :key="g.chave">
        <div class="mb-8px flex flex-wrap items-center gap-8px">
          <span class="text-14px font-bold">{{ g.titulo(r) }}</span>
          <el-tag
            :type="(r as any)[g.chave].length ? g.cor : 'success'"
            disable-transitions
            round
            size="small"
          >
            {{ (r as any)[g.chave].length }}
          </el-tag>
        </div>
        <div class="ajuda mb-8px">{{ g.ajuda }}</div>
        <el-table
          v-if="(r as any)[g.chave].length"
          :data="(r as any)[g.chave]"
          class="cursor-pointer"
          max-height="320"
          size="small"
          @row-click="(u: IdentidadeApi.UsuarioLinha) => emit('abrir', u.id)"
        >
          <el-table-column label="Nome" min-width="180">
            <template #default="{ row }">
              {{ row.nickname }} <span class="ajuda">{{ row.username }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="Unidade"
            min-width="160"
            prop="unidadeNome"
            show-overflow-tooltip
          />
          <el-table-column label="Perfis" min-width="200">
            <template #default="{ row }">
              <el-tag
                v-for="p in row.perfis"
                :key="p.id"
                class="mr-4px"
                disable-transitions
                size="small"
                type="info"
              >
                {{ p.nome }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Último acesso" width="140">
            <template #default="{ row }">{{ quando(row.ultimoAcesso) }}</template>
          </el-table-column>
          <el-table-column label="Situação" width="100">
            <template #default="{ row }">{{ row.status === 0 ? 'Ativo' : 'Inativo' }}</template>
          </el-table-column>
          <el-table-column label="Sessões" width="80" prop="sessoes" align="right" />
        </el-table>
      </ContentWrap>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import * as IdentidadeApi from '@/api/identidade'

const emit = defineEmits<{ abrir: [id: number] }>()
const message = useMessage()
const r = ref<IdentidadeApi.RevisaoAcessos>()
const carregando = ref(false)

const GRUPOS: {
  chave: keyof IdentidadeApi.RevisaoAcessos
  titulo: (r: IdentidadeApi.RevisaoAcessos) => string
  ajuda: string
  cor: 'danger' | 'warning' | 'info'
}[] = [
  {
    chave: 'desativadosNoAd',
    titulo: () => 'Desativados no AD que ainda têm acesso aqui',
    ajuda:
      'Conta desativada no AD, mas ativa no portal ou com sessão aberta. Desative ou encerre as sessões.',
    cor: 'danger'
  },
  {
    chave: 'privilegiadosSemMfa',
    titulo: () => 'Acesso administrativo sem verificação em duas etapas',
    ajuda: 'Quem administra usuários, perfis ou permissões deveria ter o segundo fator ligado.',
    cor: 'danger'
  },
  {
    chave: 'privilegiados',
    titulo: () => 'Com acesso administrativo',
    ajuda:
      'Administrador ou perfil que cria usuários, troca senhas, dá perfis ou muda permissões. Confira se todos ainda precisam.',
    cor: 'warning'
  },
  {
    chave: 'semAcesso',
    titulo: (x) => `Ativos sem entrar há mais de ${x.diasSemAcesso} dias`,
    ajuda: 'Conta parada é porta aberta: desative quem não usa mais o sistema.',
    cor: 'warning'
  },
  {
    chave: 'locais',
    titulo: () => 'Contas locais (fora do AD)',
    ajuda:
      'Não acompanham o desligamento no AD: quem sai da prefeitura continua com acesso até alguém desativar aqui.',
    cor: 'info'
  }
]

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : 'nunca'

const carregar = async () => {
  carregando.value = true
  try {
    r.value = await IdentidadeApi.getRevisaoAcessos()
  } finally {
    carregando.value = false
  }
}

const revisado = async () => {
  let observacao = ''
  try {
    const resp = await ElMessageBox.prompt(
      'Fica registrado na auditoria quem revisou, quando e o que estava na lista. Observação (opcional):',
      'Marcar como revisado',
      { confirmButtonText: 'Registrar', cancelButtonText: 'Cancelar', inputValue: '' }
    )
    observacao = resp.value
  } catch {
    return
  }
  await IdentidadeApi.marcarRevisado(observacao)
  message.success('Revisão registrada')
  await carregar()
}

defineExpose({ carregar })
onMounted(carregar)
</script>

<style lang="scss" scoped>
.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
