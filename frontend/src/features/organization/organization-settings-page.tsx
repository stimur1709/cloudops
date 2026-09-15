import { SettingsSection } from "../monitoring/settings-section";
import { useOrganization } from "./organization-context";

export function OrganizationSettingsPage() {
  const { organization, organizationId, isManager } = useOrganization();
  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-page-title">Настройки организации</h1>
        <p className="mt-1 text-body text-foreground-muted">
          {organization.name} · настройки проверок наследуются от приложения,
          если для организации нет переопределения.
        </p>
      </header>
      <section
        aria-labelledby="organization-monitoring-settings"
        className="space-y-3"
      >
        <div>
          <h2
            id="organization-monitoring-settings"
            className="text-section-title"
          >
            Настройки мониторинга
          </h2>
          <p className="mt-1 text-body text-foreground-muted">
            Для каждого типа проверки показаны итоговые значения и их источник.
          </p>
        </div>
        <SettingsSection
          scope="organization"
          organizationId={organizationId}
          isManager={isManager}
        />
      </section>
    </section>
  );
}
