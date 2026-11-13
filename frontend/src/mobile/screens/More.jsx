import { useState } from 'react';
import { api } from '../../api.js';
import { hasFeature, PlanUsageCard, PricingPlans, UpgradeNotice } from '../../Plans.jsx';
import { AvailabilitySection, GearPanel, PayoutsSection } from '../../Crew.jsx';
import { InsightsPanel } from '../../Insights.jsx';
import { ShowcasePanel, StudioSettingsPanel } from '../../Studio.jsx';
import { LeadCaptureSettings, QuoteCatalogSettings } from '../../Bookings.jsx';
import { BrandingSettings, EditingStyleSettings } from '../../Delivery.jsx';
import { useTheme } from '../../theme.jsx';
import { navigate } from '../router.js';
import { useUser } from '../session.js';
import { useEmbedClass } from '../layout.js';
import { Avatar, Button, Chip, Empty, ErrorNote, Fab, Field, Icon, List, Row, Screen, Section, Segmented, Sheet, Skeleton, useAction, useResource, useToast } from '../ui.jsx';
import { telLink, whatsappLink } from '../format.js';

// Screens that reuse the website's panels inside the app frame (settings and less frequent tools).
const SECTIONS = {
  gear: { title: 'Gear', render: (user) => <GearPanel user={user} /> },
  insights: { title: 'Insights', render: (user) => (hasFeature(user, 'INSIGHTS') ? <InsightsPanel user={user} /> : <UpgradeNotice feature="INSIGHTS" title="Insights">See bookings, conversion and profit trends for your studio.</UpgradeNotice>) },
  portfolio: { title: 'Portfolio & e-albums', render: (user) => (hasFeature(user, 'PORTFOLIO') || hasFeature(user, 'E_ALBUM') ? <ShowcasePanel user={user} /> : <UpgradeNotice feature="PORTFOLIO" title="Portfolio & e-album flipbooks">Publish a public portfolio page and share page-turning e-albums with your clients.</UpgradeNotice>) },
  packages: { title: 'Packages & pricing', render: (user) => <QuoteCatalogSettings user={user} /> },
  leads: { title: 'Enquiry links', render: (user) => (hasFeature(user, 'LEAD_CAPTURE') ? <LeadCaptureSettings /> : <UpgradeNotice feature="LEAD_CAPTURE" title="Enquiry links">Get enquiries straight from Instagram, WhatsApp and your website.</UpgradeNotice>) },
  branding: { title: 'Branding & watermark', render: () => <BrandingSettings /> },
  style: { title: 'Presets, style & album designs', render: () => <EditingStyleSettings /> },
  studio: { title: 'Studio settings', render: () => <StudioSettingsPanel /> },
  plan: { title: 'Plan & usage', render: () => <><PlanUsageCard /><PricingPlans navigate={(destination) => { window.location.href = destination; }} /></> },
};

export function MoreScreen() {
  const { user } = useUser();
  const [theme, setTheme] = useTheme();
  const [busy, run] = useAction();
  async function signOut() {
    const ok = await run(() => api.logout());
    if (ok) window.location.replace('/app');
  }
  return (
    <Screen large title="More">
      <button type="button" className="m-account-card" onClick={() => navigate('/more/plan')}>
        <Avatar name={user.fullName} size={52} />
        <span><b>{user.fullName}</b><small>{user.studioName || user.email}</small>{user.entitlements?.planName && <Chip tone="brass" icon="crown">{user.entitlements.planName}</Chip>}</span>
        <Icon name="chevron" size={18} />
      </button>

      <Section title="Studio">
        <List>
          <Row icon="users" title="Customers" onClick={() => navigate('/customers')} />
          <Row icon="calendar" title="Calendar" onClick={() => navigate('/calendar')} />
          <Row icon="team" title="Crew" subtitle="Team, freelancers, availability, payouts" onClick={() => navigate('/team')} />
          <Row icon="box" title="Gear" subtitle="Your kit and packing lists" onClick={() => navigate('/more/gear')} />
          <Row icon="chart" title="Insights" onClick={() => navigate('/more/insights')} />
        </List>
      </Section>

      <Section title="Bookings & brand">
        <List>
          <Row icon="tag" title="Packages & pricing" onClick={() => navigate('/more/packages')} />
          <Row icon="link" title="Enquiry links" subtitle="For Instagram, WhatsApp and your website" onClick={() => navigate('/more/leads')} />
          <Row icon="book" title="Portfolio & e-albums" onClick={() => navigate('/more/portfolio')} />
          <Row icon="brush" title="Branding & watermark" onClick={() => navigate('/more/branding')} />
          <Row icon="sparkle" title="Presets & editing style" onClick={() => navigate('/more/style')} />
        </List>
      </Section>

      <Section title="Account">
        <List>
          <Row icon="settings" title="Studio settings" subtitle="UPI, GST, receipts, reminders" onClick={() => navigate('/more/studio')} />
          <Row icon="crown" title="Plan & usage" onClick={() => navigate('/more/plan')} />
          <label className="m-row m-switch-line">
            <span className="m-row-icon"><Icon name="moon" size={20} /></span>
            <span className="m-row-text"><span className="m-row-title">Dark mode</span></span>
            <input type="checkbox" className="m-switch" checked={theme === 'dark'} onChange={(event) => setTheme(event.target.checked ? 'dark' : 'light')} />
          </label>
          <Row icon="globe" title="Open the full website" subtitle="Desktop layout, for anything not here" href="/dashboard?desktop=1" />
          {user.admin && <Row icon="crown" title="Admin portal" href="/admin" />}
          <Row icon="logout" title={busy ? 'Signing out…' : 'Sign out'} tone="danger" onClick={signOut} chevron={false} />
        </List>
      </Section>
      <p className="m-footnote">FrameBucket · {user.email}</p>
    </Screen>
  );
}

export function MoreSectionScreen({ section }) {
  const { user } = useUser();
  const config = SECTIONS[section];
  const embedClass = useEmbedClass();
  if (!config) return <Screen back backTo="/more" title="Not found"><Empty title="This page doesn't exist" /></Screen>;
  return (
    <Screen back backTo="/more" title={config.title}>
      <div className={embedClass}>{config.render(user)}</div>
    </Screen>
  );
}

// ------------------------------------------------------------------------------------- team

const SKILLS = [['PHOTOGRAPHY', 'Photography'], ['VIDEOGRAPHY', 'Videography'], ['CANDID_PHOTOGRAPHY', 'Candid'], ['CINEMATIC_VIDEOGRAPHY', 'Cinematic video'], ['PHOTO_EDITING', 'Photo editing'], ['VIDEO_EDITING', 'Video editing'], ['ALBUM_DESIGNING', 'Album design']];
const LANGUAGES = [['KANNADA', 'Kannada'], ['ENGLISH', 'English'], ['HINDI', 'Hindi'], ['TAMIL', 'Tamil'], ['TELUGU', 'Telugu'], ['MALAYALAM', 'Malayalam']];

export function TeamScreen() {
  const { user } = useUser();
  const [section, setSection] = useState('members');
  const embedClass = useEmbedClass();
  const team = useResource(() => api.teamMembers(), []);
  const [view, setView] = useState('TEAM_MEMBER');
  const [adding, setAdding] = useState(false);
  const [busy, run] = useAction();
  const list = (team.data || []).filter((member) => member.memberType === view);
  const toast = useToast();

  async function resend(member) {
    const result = await run(() => api.resendTeamInvite(member.memberType, member.id), 'A fresh invite link is ready.');
    if (result?.invitationUrl) shareInvite(result, toast);
  }

  return (
    <Screen back backTo="/more" title="Crew" onRefresh={team.reload} fab={section === 'members' ? <Fab label="Add crew member" onClick={() => setAdding(true)} /> : null}>
      <div className="m-sticky-tools">
        <Segmented value={section} onChange={setSection} options={[['members', 'Members'], ['availability', 'Availability'], ['payouts', 'Payouts']]} />
        {section === 'members' && <Segmented value={view} onChange={setView} options={[['TEAM_MEMBER', 'Team', (team.data || []).filter((m) => m.memberType === 'TEAM_MEMBER').length], ['FREELANCER', 'Freelancers', (team.data || []).filter((m) => m.memberType === 'FREELANCER').length]]} />}
      </div>
      {section !== 'members' && (
        <div className={embedClass}>
          {!hasFeature(user, 'CREW_PAYOUTS')
            ? <UpgradeNotice feature="CREW_PAYOUTS" title="Crew scheduling & payouts">Mark when people are unavailable, spot clashes and track what each person is owed.</UpgradeNotice>
            : section === 'availability' ? <AvailabilitySection /> : <PayoutsSection />}
        </div>
      )}
      {section === 'members' && <>
      <ErrorNote message={team.error} onRetry={team.reload} />
      {!team.data ? <Skeleton /> : !list.length ? <Empty icon="team" title={view === 'FREELANCER' ? 'No freelancers yet' : 'No team members yet'} action={<Button onClick={() => setAdding(true)}>Add someone</Button>}>Send an invite link and they fill in their own profile.</Empty> : (
        <div className="m-list">
          {list.map((member) => (
            <div key={member.id} className="m-team-row">
              <Avatar name={member.name} />
              <div className="m-row-text">
                <span className="m-row-title">{member.name}</span>
                <span className="m-row-subtitle">{(member.skills || []).map((skill) => SKILLS.find(([key]) => key === skill)?.[1] || skill).join(', ') || member.mobileNumber}</span>
                {member.status !== 'ACTIVE' && <span><Chip tone="warn">Invite pending</Chip> <button type="button" className="m-link" disabled={busy} onClick={() => resend(member)}>Send again</button></span>}
              </div>
              <a className="m-icon-button" href={telLink(member.mobileNumber)} aria-label={`Call ${member.name}`}><Icon name="phone" size={20} /></a>
              <a className="m-icon-button is-whatsapp" href={whatsappLink(member.mobileNumber)} aria-label={`WhatsApp ${member.name}`}><Icon name="chat" size={20} /></a>
            </div>
          ))}
        </div>
      )}
      </>}
      <AddMemberSheet open={adding} memberType={view} onClose={() => setAdding(false)} onSaved={(member) => team.setData((current) => [member, ...(current || [])])} />
    </Screen>
  );
}

async function shareInvite(result, toast) {
  const text = `Hi ${result.member.name.split(' ')[0]}, please complete your FrameBucket profile: ${result.invitationUrl}`;
  if (navigator.share) {
    try { await navigator.share({ text }); return; } catch { /* cancelled */ }
  }
  try { await navigator.clipboard.writeText(result.invitationUrl); toast('Invite link copied.'); } catch { window.location.href = whatsappLink(result.member.mobileNumber, text); }
}

function AddMemberSheet({ open, memberType, onClose, onSaved }) {
  const empty = { name: '', mobileNumber: '', memberType, age: '', languages: [], skills: [] };
  const [form, setForm] = useState(empty);
  const [full, setFull] = useState(false);
  const [busy, run] = useAction();
  const toast = useToast();
  const toggle = (key, value) => setForm({ ...form, [key]: form[key].includes(value) ? form[key].filter((item) => item !== value) : [...form[key], value] });
  async function submit(event) {
    event.preventDefault();
    const payload = { name: form.name.trim(), mobileNumber: form.mobileNumber.trim(), memberType: form.memberType || memberType, age: form.age ? Number(form.age) : null, languages: form.languages, skills: form.skills };
    if (full) {
      const member = await run(() => api.createTeamMember(payload), (created) => `${created.name} added.`);
      if (member) { onSaved(member); setForm(empty); onClose(); }
    } else {
      const result = await run(() => api.inviteTeamMember(payload), (created) => `Invite ready for ${created.member.name}.`);
      if (result) { onSaved(result.member); setForm(empty); onClose(); shareInvite(result, toast); }
    }
  }
  return (
    <Sheet open={open} title={memberType === 'FREELANCER' ? 'Add a freelancer' : 'Add a team member'} onClose={onClose} tall>
      <form className="m-form" onSubmit={submit}>
        <Segmented value={full ? 'full' : 'invite'} onChange={(value) => setFull(value === 'full')} options={[['invite', 'Send invite link'], ['full', 'Fill in myself']]} />
        <Field label="Name"><input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required maxLength="120" /></Field>
        <Field label="Mobile number"><input type="tel" inputMode="tel" value={form.mobileNumber} onChange={(event) => setForm({ ...form, mobileNumber: event.target.value })} required maxLength="13" pattern="([0-9]{10}|\+91[0-9]{10})" /></Field>
        <div className="m-choice-row">
          {[['TEAM_MEMBER', 'Team member'], ['FREELANCER', 'Freelancer']].map(([key, text]) => <button key={key} type="button" className={(form.memberType || memberType) === key ? 'is-active' : ''} onClick={() => setForm({ ...form, memberType: key })}>{text}</button>)}
        </div>
        {full && (
          <>
            <Field label="Age"><input type="number" inputMode="numeric" min="16" max="100" value={form.age} onChange={(event) => setForm({ ...form, age: event.target.value })} required /></Field>
            <span className="m-field-label">Skills</span>
            <div className="m-choice-wrap">{SKILLS.map(([key, text]) => <button key={key} type="button" className={form.skills.includes(key) ? 'is-active' : ''} onClick={() => toggle('skills', key)}>{text}</button>)}</div>
            <span className="m-field-label">Languages</span>
            <div className="m-choice-wrap">{LANGUAGES.map(([key, text]) => <button key={key} type="button" className={form.languages.includes(key) ? 'is-active' : ''} onClick={() => toggle('languages', key)}>{text}</button>)}</div>
          </>
        )}
        {!full && <p className="m-muted m-small">They get a link to add their own skills and languages.</p>}
        <Button type="submit" block busy={busy} disabled={full && (!form.skills.length || !form.languages.length)}>{full ? 'Save' : 'Create invite link'}</Button>
      </form>
    </Sheet>
  );
}
