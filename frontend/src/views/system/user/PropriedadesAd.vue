<!-- Propriedades da conta lidas na hora do AD, nas mesmas abas do "Usuários e Computadores do Active Directory". -->
<template>
  <div v-loading="carregando">
    <el-alert v-if="erro" :closable="false" :title="erro" show-icon type="warning">
      <el-button class="mt-6px" size="small" @click="carregar">Tentar de novo</el-button>
    </el-alert>

    <template v-if="p">
      <div class="mb-10px flex flex-wrap items-center gap-6px">
        <el-tag :type="p.ativa ? 'success' : 'danger'" disable-transitions>
          {{ p.ativa ? 'Conta ativa no AD' : 'Conta desativada no AD' }}
        </el-tag>
        <el-tag v-if="p.bloqueada" disable-transitions type="danger">
          Bloqueada{{ p.bloqueadaEm ? ' desde ' + quando(p.bloqueadaEm) : '' }}
        </el-tag>
        <el-tag v-if="p.senhaExpirada" disable-transitions type="danger">Senha expirada</el-tag>
        <el-tag v-if="p.trocarSenhaNoLogon" disable-transitions type="warning">
          Troca a senha no próximo logon
        </el-tag>
        <el-tag v-if="p.senhaNuncaExpira" disable-transitions type="info">Senha nunca expira</el-tag>
        <el-tag v-if="p.exigeCartaoInteligente" disable-transitions type="info">
          Exige cartão inteligente
        </el-tag>
        <span class="flex-1"></span>
        <span class="ajuda">Lido agora{{ p.servidor ? ' de ' + p.servidor : '' }}</span>
        <el-button link type="primary" @click="carregar">
          <Icon class="mr-2px" icon="ep:refresh" /> Atualizar
        </el-button>
      </div>

      <el-tabs v-model="aba">
        <el-tab-pane label="Conta" name="conta">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="Login (pré-Windows 2000)">{{ v(p.login) }}</el-descriptions-item>
            <el-descriptions-item label="Logon (UPN)">{{ v(p.upn) }}</el-descriptions-item>
            <el-descriptions-item label="Último logon com sucesso">
              {{ quando(p.ultimoLogon) }}
              <el-tooltip
                content="lastLogon: vale só para o controlador que respondeu (não replica). Se a pessoa entrou por outro DC, a data mais recente pode estar no logon replicado."
              >
                <Icon class="ml-2px align-middle" icon="ep:info-filled" />
              </el-tooltip>
            </el-descriptions-item>
            <el-descriptions-item label="Último logon (replicado)">
              {{ quando(p.ultimoLogonReplicado) }}
              <el-tooltip
                content="lastLogonTimestamp: igual em todos os DCs, mas atualiza com atraso de até 14 dias."
              >
                <Icon class="ml-2px align-middle" icon="ep:info-filled" />
              </el-tooltip>
            </el-descriptions-item>
            <el-descriptions-item label="Último logon com senha errada">
              {{ quando(p.ultimaSenhaErrada) }}
            </el-descriptions-item>
            <el-descriptions-item label="Senhas erradas seguidas">
              {{ p.senhasErradas ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="Quantidade de logons">
              {{ p.quantidadeLogons ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="Conta expira em">
              {{ p.contaExpiraEm ? quando(p.contaExpiraEm) : 'Nunca' }}
            </el-descriptions-item>
            <el-descriptions-item label="Senha alterada em">
              {{ p.trocarSenhaNoLogon ? 'Pendente (troca no próximo logon)' : quando(p.senhaAlteradaEm) }}
            </el-descriptions-item>
            <el-descriptions-item label="Senha expira em">
              {{ p.senhaNuncaExpira ? 'Nunca' : quando(p.senhaExpiraEm) }}
            </el-descriptions-item>
            <el-descriptions-item label="Conta criada em">{{ quando(p.criadaEm) }}</el-descriptions-item>
            <el-descriptions-item label="Alterada no AD em">{{ quando(p.alteradaEm) }}</el-descriptions-item>
            <el-descriptions-item :span="2" label="Local (DN)">
              <span class="break-all">{{ p.dn }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane :label="`Membro de (${p.grupos.length})`" name="grupos">
          <div class="mb-8px flex flex-wrap items-center gap-8px">
            <el-input
              v-model="filtroGrupo"
              class="!w-240px"
              clearable
              placeholder="Filtrar grupos"
              size="small"
            />
            <el-checkbox v-model="soDiretos" size="small">Só os diretos</el-checkbox>
            <span v-if="p.grupoPrincipal" class="ajuda">
              Grupo principal: <b>{{ p.grupoPrincipal }}</b>
            </span>
          </div>
          <el-table :data="grupos" max-height="420" size="small">
            <el-table-column label="Grupo" min-width="200" prop="nome" sortable />
            <el-table-column label="Como" width="110">
              <template #default="{ row }">
                <el-tag :type="row.direto ? 'primary' : 'info'" disable-transitions size="small">
                  {{ row.direto ? 'direto' : 'herdado' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Local" min-width="260" prop="dn" show-overflow-tooltip />
          </el-table>
          <div class="ajuda mt-6px"
            >"Herdado" = a pessoa está num grupo que é membro deste (grupos aninhados).</div
          >
        </el-tab-pane>

        <el-tab-pane label="Organização" name="organizacao">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="Cargo">{{ v(p.cargo) }}</el-descriptions-item>
            <el-descriptions-item label="Departamento">{{ v(p.departamento) }}</el-descriptions-item>
            <el-descriptions-item label="Empresa">{{ v(p.empresa) }}</el-descriptions-item>
            <el-descriptions-item label="Gestor">
              <el-tooltip v-if="p.gestorDn" :content="p.gestorDn">
                <span>{{ p.gestorNome }}</span>
              </el-tooltip>
              <template v-else>—</template>
            </el-descriptions-item>
            <el-descriptions-item label="Matrícula (employeeID)">{{
              v(p.matricula)
            }}</el-descriptions-item>
            <el-descriptions-item label="Nº do funcionário">{{
              v(p.numeroFuncionario)
            }}</el-descriptions-item>
            <el-descriptions-item :span="2" :label="`Subordinados (${p.subordinados.length})`">
              <el-tag
                v-for="s in p.subordinados"
                :key="s"
                class="mb-2px mr-4px"
                disable-transitions
                size="small"
                type="info"
              >
                {{ s }}
              </el-tag>
              <template v-if="!p.subordinados.length">—</template>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="Perfil" name="perfil">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="Caminho do perfil">{{
              v(p.caminhoPerfil)
            }}</el-descriptions-item>
            <el-descriptions-item label="Script de logon">{{ v(p.scriptLogon) }}</el-descriptions-item>
            <el-descriptions-item label="Pasta base">
              {{ p.unidadePastaBase ? p.unidadePastaBase + ' → ' : '' }}{{ v(p.pastaBase) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="Geral e contato" name="geral">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="Nome de exibição">{{ v(p.nome) }}</el-descriptions-item>
            <el-descriptions-item label="Nome / sobrenome">
              {{ [p.nomeProprio, p.sobrenome].filter(Boolean).join(' ') || '—' }}
            </el-descriptions-item>
            <el-descriptions-item :span="2" label="Descrição">{{
              v(p.descricao)
            }}</el-descriptions-item>
            <el-descriptions-item label="Escritório">{{ v(p.escritorio) }}</el-descriptions-item>
            <el-descriptions-item label="E-mail">{{ v(p.email) }}</el-descriptions-item>
            <el-descriptions-item label="Telefone">{{ v(p.telefone) }}</el-descriptions-item>
            <el-descriptions-item label="Celular">{{ v(p.celular) }}</el-descriptions-item>
            <el-descriptions-item label="Telefone IP">{{ v(p.telefoneIp) }}</el-descriptions-item>
            <el-descriptions-item label="Residencial">{{
              v(p.telefoneResidencial)
            }}</el-descriptions-item>
            <el-descriptions-item :span="2" label="Endereço">
              {{
                [p.endereco, p.cidade, p.estado, p.cep, p.pais].filter(Boolean).join(' · ') || '—'
              }}
            </el-descriptions-item>
            <el-descriptions-item :span="2" label="Página web">{{
              v(p.paginaWeb)
            }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script lang="ts" setup>
import * as IdentidadeApi from '@/api/identidade'

const props = defineProps<{ id: number }>()
const p = ref<IdentidadeApi.AdPropriedades>()
const erro = ref<string>()
const carregando = ref(false)
const aba = ref('conta')
const filtroGrupo = ref('')
const soDiretos = ref(false)

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : 'nunca'
const v = (x?: string) => (x && x.trim() ? x : '—')

const grupos = computed(() => {
  const f = filtroGrupo.value.trim().toLowerCase()
  return (p.value?.grupos || []).filter(
    (g) => (!soDiretos.value || g.direto) && (!f || g.nome.toLowerCase().includes(f))
  )
})

const carregar = async () => {
  carregando.value = true
  erro.value = undefined
  try {
    p.value = await IdentidadeApi.getPropriedadesAd(props.id)
  } catch (e: any) {
    p.value = undefined
    erro.value = e?.msg || e?.message || 'Não foi possível ler o AD agora.'
  } finally {
    carregando.value = false
  }
}

watch(() => props.id, carregar)
onMounted(carregar)
</script>

<style lang="scss" scoped>
.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
